package com.smsagent

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.smsagent.dispatcher.SmsDispatcher
import com.smsagent.dispatcher.SmsSource
import com.smsagent.keepalive.KeepAliveService
import com.smsagent.sms.IncomingSms
import com.smsagent.state.AgentStateStore
import com.smsagent.state.BureauSealController
import com.smsagent.state.EventLogStore
import com.smsagent.util.MiuiPermissionHelper
import com.smsagent.util.TimeFormatter
import kotlin.concurrent.thread

class MainActivity : Activity() {

    // Tab 容器
    private lateinit var mainRoot: View
    private lateinit var tabStatusContainer: View
    private lateinit var tabConfigContainer: View
    private lateinit var tabHistoryContainer: View
    private lateinit var tabConsoleContainer: View
    private lateinit var bottomNavigationDock: View
    private lateinit var navigationButtons: List<View>
    private var selectedTabIndex = 0
    private var keyboardVisible = false
    private val navigationInterpolator = DecelerateInterpolator(1.8f)

    private val keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val visibleFrame = Rect()
        mainRoot.getWindowVisibleDisplayFrame(visibleFrame)
        val keyboardHeight = mainRoot.rootView.height - visibleFrame.height()
        val isKeyboardVisible = keyboardHeight > mainRoot.rootView.height * KEYBOARD_HEIGHT_THRESHOLD
        if (keyboardVisible != isKeyboardVisible) {
            keyboardVisible = isKeyboardVisible
            updateNavigationVisibility()
        }
    }

    // === TAB 3 (原TAB 1): 状态/信息页组件 ===
    private lateinit var permissionValue: TextView
    private lateinit var miuiNotificationSmsLabel: TextView
    private lateinit var miuiNotificationSmsValue: TextView
    private lateinit var openMiuiNotificationSmsButton: MaterialButton
    private lateinit var lastTriggerValue: TextView
    private lateinit var lastForwardResultValue: TextView

    // === TAB 1 (原TAB 2): 配置页组件 ===
    private lateinit var barkApiInputLayout: TextInputLayout
    private lateinit var barkApiInput: TextInputEditText
    private lateinit var saveBarkApiButton: MaterialButton
    private lateinit var openAppSettingsButton: MaterialButton

    // === TAB 2 (原TAB 3): 历史短信组件 ===
    private lateinit var selectAllPanel: View
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var smsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var forwardSelectedButton: MaterialButton
    private lateinit var historyAdapter: SmsHistoryAdapter
    private var smsItems: List<SmsHistoryItem> = emptyList()

    // === TAB 4: 日志终端组件 ===
    private lateinit var consoleOutput: TextView
    private lateinit var consoleInput: TextInputEditText
    private lateinit var appendInputButton: MaterialButton
    private lateinit var refreshButton: MaterialButton
    private lateinit var copyButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var clearLogButtonConsole: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化内容容器与导航坞
        mainRoot = findViewById(R.id.mainRoot)
        tabStatusContainer = findViewById(R.id.tabStatusContainer)
        tabConfigContainer = findViewById(R.id.tabConfigContainer)
        tabHistoryContainer = findViewById(R.id.tabHistoryContainer)
        tabConsoleContainer = findViewById(R.id.tabConsoleContainer)
        bottomNavigationDock = findViewById(R.id.bottomNavigationDock)
        navigationButtons = listOf(
            findViewById(R.id.navConfigButton),
            findViewById(R.id.navHistoryButton),
            findViewById(R.id.navStatusButton),
            findViewById(R.id.navConsoleButton),
        )
        mainRoot.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)

        // 2. 绑定 TAB 3 (状态/信息页) 组件
        permissionValue = findViewById(R.id.permissionValue)
        miuiNotificationSmsLabel = findViewById(R.id.miuiNotificationSmsLabel)
        miuiNotificationSmsValue = findViewById(R.id.miuiNotificationSmsValue)
        openMiuiNotificationSmsButton = findViewById(R.id.openMiuiNotificationSmsButton)
        lastTriggerValue = findViewById(R.id.lastTriggerValue)
        lastForwardResultValue = findViewById(R.id.lastForwardResultValue)

        // 绑定版本文本并动态读取包体版本
        val appVersionText = findViewById<TextView>(R.id.appVersionText)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.2.4"
        }
        appVersionText.text = "${getString(R.string.about_version)} · v$versionName"
        appVersionText.setOnLongClickListener {
            BureauSealController.recordVersionLongPress(this)
            true
        }

        // 3. 绑定 TAB 1 (配置页) 组件
        barkApiInputLayout = findViewById(R.id.barkApiInputLayout)
        barkApiInput = findViewById(R.id.barkApiInput)
        saveBarkApiButton = findViewById(R.id.saveBarkApiButton)
        openAppSettingsButton = findViewById(R.id.openAppSettingsButton)

        // 4. 绑定 TAB 2 (历史短信) 组件
        selectAllPanel = findViewById(R.id.selectAllPanel)
        selectAllCheckbox = findViewById(R.id.selectAllCheckbox)
        smsRecyclerView = findViewById(R.id.smsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        loadingProgress = findViewById(R.id.loadingProgress)
        forwardSelectedButton = findViewById(R.id.forwardSelectedButton)

        // 5. 绑定 TAB 4 (日志终端) 组件
        consoleOutput = findViewById(R.id.consoleOutput)
        consoleInput = findViewById(R.id.consoleInput)
        appendInputButton = findViewById(R.id.appendInputButton)
        refreshButton = findViewById(R.id.refreshButton)
        copyButton = findViewById(R.id.copyButton)
        exportButton = findViewById(R.id.exportButton)
        clearLogButtonConsole = findViewById(R.id.clearLogButtonConsole)

        // 仅在小米设备上显示通知类短信相关卡片
        if (MiuiPermissionHelper.isMiuiDevice()) {
            findViewById<android.view.View>(R.id.miuiSmsPermissionCard).visibility = android.view.View.VISIBLE
            openMiuiNotificationSmsButton.visibility = android.view.View.VISIBLE
        }

        // 绑定关于页面 GitHub 跳转事件
        findViewById<TextView>(R.id.githubText).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/123zpc/SMS-SENDER.git"))
            startActivity(intent)
        }
        findViewById<MaterialButton>(R.id.replayOnboardingFromConfigButton).setOnClickListener {
            openOnboardingPreview()
        }
        findViewById<MaterialButton>(R.id.replayOnboardingFromStatusButton).setOnClickListener {
            openOnboardingPreview()
        }

        // 配置页事件
        barkApiInput.setText(AgentStateStore.getRemoteApiTemplate(this))
        saveBarkApiButton.setOnClickListener { saveBarkApiUrl() }
        openAppSettingsButton.setOnClickListener { openAppSettings() }
        openMiuiNotificationSmsButton.setOnClickListener { openMiuiNotificationSmsSettings() }

        // 历史页事件初始化
        smsRecyclerView.layoutManager = LinearLayoutManager(this)
        historyAdapter = SmsHistoryAdapter(emptyList()) { selectedCount ->
            updateForwardButtonState(selectedCount)
        }
        smsRecyclerView.adapter = historyAdapter

        selectAllPanel.setOnClickListener {
            val nextChecked = !selectAllCheckbox.isChecked
            selectAllCheckbox.isChecked = nextChecked
            historyAdapter.selectAll(nextChecked)
        }
        selectAllCheckbox.setOnClickListener {
            historyAdapter.selectAll(selectAllCheckbox.isChecked)
        }
        forwardSelectedButton.setOnClickListener {
            forwardSelectedSms()
        }

        // 终端页事件
        appendInputButton.setOnClickListener { appendManualInput() }
        refreshButton.setOnClickListener { renderConsole() }
        copyButton.setOnClickListener { copyConsole() }
        exportButton.setOnClickListener { exportConsole() }
        clearLogButtonConsole.setOnClickListener { clearConsole() }

        // 导航坞始终以居中的图标和文字呈现，不再使用会横移的系统底部导航。
        navigationButtons.forEachIndexed { index, button ->
            button.setOnClickListener { switchTab(index) }
        }

        requestRequiredPermissionsIfNeeded()
        KeepAliveService.start(this)
        
        // 初始装载配置 Tab (Tab 1, 索引为 0)
        switchTab(0)

        if (!hasMissingStandardPermissions()) {
            MiuiPermissionHelper.showNotificationSmsGuideIfNeeded(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentTab()
    }

    override fun onDestroy() {
        if (::mainRoot.isInitialized && mainRoot.viewTreeObserver.isAlive) {
            mainRoot.viewTreeObserver.removeOnGlobalLayoutListener(keyboardLayoutListener)
        }
        super.onDestroy()
    }

    private fun switchTab(index: Int) {
        showSelectedTab(index)
        selectedTabIndex = index
        updateNavigationSelection()
        updateNavigationVisibility()

        when (index) {
            1 -> checkPermissionAndLoadSms()
            2 -> renderState()
            3 -> renderConsole()
        }
    }

    private fun updateNavigationSelection() {
        navigationButtons.forEachIndexed { index, button ->
            val isSelected = index == selectedTabIndex
            button.isActivated = isSelected
            button.animate().cancel()
            if (isSelected) {
                // 选中时 LED 点轻微点亮回弹，克制不弹跳
                button.scaleX = 0.92f
                button.scaleY = 0.92f
            }
            button.animate()
                .alpha(if (isSelected) 1f else 0.6f)
                .scaleX(if (isSelected) 1f else 0.96f)
                .scaleY(if (isSelected) 1f else 0.96f)
                .setDuration(NAVIGATION_MOTION_DURATION)
                .setInterpolator(navigationInterpolator)
                .start()
        }
    }

    private fun dpToPixels(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun updateNavigationVisibility() {
        bottomNavigationDock.visibility = if (selectedTabIndex == CONSOLE_TAB && keyboardVisible) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun openOnboardingPreview() {
        startActivity(
            Intent(this, OnboardingActivity::class.java)
                .putExtra(OnboardingActivity.EXTRA_PREVIEW, true),
        )
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun showSelectedTab(index: Int) {
        val selectedTab = when (index) {
            0 -> tabConfigContainer
            1 -> tabHistoryContainer
            2 -> tabStatusContainer
            else -> tabConsoleContainer
        }

        listOf(
            tabConfigContainer,
            tabHistoryContainer,
            tabStatusContainer,
            tabConsoleContainer,
        ).filter { tab -> tab !== selectedTab }.forEach { tab ->
            tab.animate().cancel()
            tab.visibility = View.GONE
            tab.alpha = 1f
            tab.translationY = 0f
        }

        if (selectedTab.visibility != View.VISIBLE) {
            selectedTab.alpha = 0f
            selectedTab.translationY = 14f
            selectedTab.visibility = View.VISIBLE
            selectedTab.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start()
        }
    }

    private fun refreshCurrentTab() {
        if (tabHistoryContainer.visibility == View.VISIBLE) {
            checkPermissionAndLoadSms()
        } else if (tabStatusContainer.visibility == View.VISIBLE) {
            renderState()
        } else if (tabConsoleContainer.visibility == View.VISIBLE) {
            renderConsole()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RUNTIME_PERMISSIONS_REQUEST_CODE) {
            KeepAliveService.start(this)
            refreshCurrentTab()
            MiuiPermissionHelper.showNotificationSmsGuideIfNeeded(this)
        } else if (requestCode == REQUEST_READ_SMS_PERMISSION_TAB) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                loadSmsFromInbox()
            } else {
                emptyStateText.visibility = View.VISIBLE
            }
        }
    }

    private fun buildRequiredPermissions(): List<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasMissingStandardPermissions(): Boolean {
        return buildRequiredPermissions().any { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissionsIfNeeded() {
        val missingPermissions = buildRequiredPermissions().filter { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), RUNTIME_PERMISSIONS_REQUEST_CODE)
        }
    }

    // === TAB 3 (原TAB 1): 状态与信息页渲染 ===
    private fun renderState() {
        permissionValue.text = buildString {
            append("RECEIVE_SMS：")
            append(permissionText(hasReceiveSmsPermission()))
            append('\n')
            append("READ_SMS：")
            append(permissionText(hasReadSmsPermission()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                append('\n')
                append("POST_NOTIFICATIONS：")
                append(permissionText(hasPostNotificationsPermission()))
            }
        }

        if (MiuiPermissionHelper.isMiuiDevice()) {
            val granted = MiuiPermissionHelper.hasNotificationSmsPermission(this)
            miuiNotificationSmsValue.text = when (granted) {
                true -> {
                    openMiuiNotificationSmsButton.isEnabled = false
                    openMiuiNotificationSmsButton.text = "通知类短信权限已开启"
                    getString(R.string.permission_granted)
                }
                false -> {
                    openMiuiNotificationSmsButton.isEnabled = true
                    openMiuiNotificationSmsButton.text = getString(R.string.open_miui_notification_sms_settings)
                    getString(R.string.permission_denied)
                }
                null -> {
                    openMiuiNotificationSmsButton.isEnabled = true
                    openMiuiNotificationSmsButton.text = getString(R.string.open_miui_notification_sms_settings)
                    "未知（无法检测）"
                }
            }
        }

        val lastTriggerTime = AgentStateStore.getLastTriggerTime(this)
        lastTriggerValue.text = if (lastTriggerTime == 0L) {
            getString(R.string.last_trigger_empty)
        } else {
            TimeFormatter.format(lastTriggerTime)
        }

        lastForwardResultValue.text = AgentStateStore.getLastForwardResult(this)
            .ifBlank { getString(R.string.last_forward_empty) }
    }

    // === TAB 1 (原TAB 2): 配置项保存与跳转 ===
    private fun saveBarkApiUrl() {
        val value = barkApiInput.text?.toString()?.trim().orEmpty()
        if (!isHttpUrl(value)) {
            barkApiInputLayout.error = getString(R.string.bark_api_error)
            return
        }

        barkApiInputLayout.error = null
        AgentStateStore.saveRemoteApiTemplate(this, value)
        
        // 核心修复逻辑：在成功配置 Bark API 的瞬间，同步记录当前短信库的最新 ID 作为已读基准。
        // 这可以防止在 Bark 配置成功前收到的旧历史短信在后续新短信到来被唤醒时进行批量补发。
        seedLastObservedSmsIdOnConfigSaved()

        EventLogStore.append(this, "配置", getString(R.string.remote_api_saved_log))
        Toast.makeText(this, R.string.remote_api_saved, Toast.LENGTH_SHORT).show()
        refreshCurrentTab()
    }

    private fun seedLastObservedSmsIdOnConfigSaved() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            thread {
                try {
                    val uri = Uri.parse("content://sms/inbox")
                    val projection = arrayOf("_id")
                    contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idCol = cursor.getColumnIndexOrThrow("_id")
                            val latestId = cursor.getLong(idCol)
                            AgentStateStore.saveLastObservedSmsId(this, latestId)
                            EventLogStore.append(this, "配置", "已对齐配置保存时的短信指针：id=$latestId")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun openMiuiNotificationSmsSettings() {
        MiuiPermissionHelper.resetGuided(this)
        MiuiPermissionHelper.showNotificationSmsGuideIfNeeded(this)
    }

    // === TAB 2 (原TAB 3): 历史短信功能 ===
    private fun checkPermissionAndLoadSms() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadSmsFromInbox()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS_PERMISSION_TAB)
        }
    }

    private fun loadSmsFromInbox() {
        loadingProgress.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
        selectAllPanel.visibility = View.GONE

        thread {
            val list = mutableListOf<SmsHistoryItem>()
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date", "read")
            try {
                contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow("_id")
                    val addressCol = cursor.getColumnIndexOrThrow("address")
                    val bodyCol = cursor.getColumnIndexOrThrow("body")
                    val dateCol = cursor.getColumnIndexOrThrow("date")
                    val readCol = cursor.getColumnIndexOrThrow("read")

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val sender = cursor.getString(addressCol).orEmpty()
                        val body = cursor.getString(bodyCol).orEmpty()
                        val timestamp = cursor.getLong(dateCol)
                        val isRead = cursor.getInt(readCol) == 1
                        list.add(SmsHistoryItem(id, sender, body, timestamp, isRead))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            runOnUiThread {
                loadingProgress.visibility = View.GONE
                smsItems = list
                historyAdapter.updateItems(smsItems)

                if (list.isEmpty()) {
                    emptyStateText.visibility = View.VISIBLE
                } else {
                    emptyStateText.visibility = View.GONE
                    selectAllPanel.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateForwardButtonState(selectedCount: Int) {
        if (selectedCount > 0) {
            forwardSelectedButton.isEnabled = true
            forwardSelectedButton.text = getString(R.string.sms_history_forward_selected, selectedCount)
        } else {
            forwardSelectedButton.isEnabled = false
            forwardSelectedButton.text = getString(R.string.sms_history_forward_none)
        }

        selectAllCheckbox.isChecked = selectedCount > 0 && selectedCount == smsItems.size
    }

    private fun forwardSelectedSms() {
        val selected = historyAdapter.getSelectedItems()
        if (selected.isEmpty()) return

        selected.forEach { item ->
            val incomingSms = IncomingSms.fromRaw(
                sender = item.sender,
                body = item.body,
                receivedAtMillis = item.timestamp
            )
            SmsDispatcher.dispatch(this, incomingSms, SmsSource.MANUAL)
        }

        Toast.makeText(
            this,
            getString(R.string.sms_history_forward_trigger_toast, selected.size),
            Toast.LENGTH_LONG
        ).show()

        historyAdapter.selectAll(false)
    }

    // === TAB 4: 日志终端功能 ===
    private fun renderConsole() {
        consoleOutput.text = EventLogStore.getConsoleText(this)
            .ifBlank { getString(R.string.console_empty) }
    }

    private fun appendManualInput() {
        val text = consoleInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            return
        }

        if (text.equals("SEAL", ignoreCase = true) || text.equals("PULSE", ignoreCase = true)) {
            consoleInput.text?.clear()
            BureauSealController.showSeal(this)
            renderConsole()
            return
        }

        EventLogStore.append(this, "输入", text)
        consoleInput.text?.clear()
        renderConsole()
    }

    private fun copyConsole() {
        val text = EventLogStore.getConsoleText(this)
        if (text.isBlank()) {
            Toast.makeText(this, R.string.console_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.console_title), text))
        Toast.makeText(this, R.string.console_copied, Toast.LENGTH_SHORT).show()
    }

    private fun clearConsole() {
        EventLogStore.clear(this)
        renderConsole()
    }

    private fun exportConsole() {
        val text = EventLogStore.getExportText(this)
        if (text.isBlank()) {
            Toast.makeText(this, R.string.console_export_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = TimeFormatter.format(System.currentTimeMillis())
            .replace(" ", "_")
            .replace(":", "-")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TITLE,
                getString(R.string.console_export_default_name, timestamp),
            )
        }
        startActivityForResult(intent, REQUEST_EXPORT_LOG_TAB)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXPORT_LOG_TAB && resultCode == RESULT_OK) {
            val targetUri = data?.data ?: return
            saveConsoleToUri(targetUri)
        }
    }

    private fun saveConsoleToUri(targetUri: Uri) {
        val text = EventLogStore.getExportText(this)
        if (text.isBlank()) {
            Toast.makeText(this, R.string.console_export_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val success = runCatching {
            contentResolver.openOutputStream(targetUri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                requireNotNull(writer) { "output stream unavailable" }
                writer.write(text)
            }
        }.isSuccess

        Toast.makeText(
            this,
            if (success) R.string.console_export_success else R.string.console_export_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    // === 辅助方法与常量 ===
    private fun hasReceiveSmsPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasReadSmsPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun permissionText(granted: Boolean): String {
        return if (granted) {
            getString(R.string.permission_granted)
        } else {
            getString(R.string.permission_denied)
        }
    }

    private fun isHttpUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        return (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }

    private companion object {
        private const val RUNTIME_PERMISSIONS_REQUEST_CODE = 1001
        private const val REQUEST_READ_SMS_PERMISSION_TAB = 2002
        private const val REQUEST_EXPORT_LOG_TAB = 2003
        private const val CONSOLE_TAB = 3
        private const val KEYBOARD_HEIGHT_THRESHOLD = 0.22
        private const val NAVIGATION_MOTION_DURATION = 180L
    }
}
