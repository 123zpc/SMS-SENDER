package com.smsagent.util

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.smsagent.R

/**
 * 小米 MIUI / HyperOS "通知类短信" 权限引导工具类。
 *
 * 由于该权限属于 MIUI 私有权限体系，Android 标准 requestPermissions() 接口无法触发
 * 系统授权弹窗，因此通过弹出引导对话框 + 跳转 MIUI 权限管理页的方式引导用户手动开启。
 */
object MiuiPermissionHelper {

    private const val PREFS_NAME = "miui_permission_prefs"
    private const val KEY_NOTIFICATION_SMS_GUIDED = "notification_sms_guided"

    /**
     * 判断当前设备是否为小米 MIUI / HyperOS 设备。
     * 通过读取系统属性 ro.miui.ui.version.name 来判断。
     */
    fun isMiuiDevice(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val value = method.invoke(null, "ro.miui.ui.version.name", "") as String
            value.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查是否已经引导过用户开启"通知类短信"权限。
     */
    fun hasGuided(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIFICATION_SMS_GUIDED, false)
    }

    /**
     * 标记已引导过用户，后续不再重复弹窗。
     */
    fun markGuided(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_SMS_GUIDED, true)
            .apply()
    }

    /**
     * 重置引导标记，使得下次检查时可以再次弹出引导对话框。
     * 用于二次入口：用户主动点击按钮时先重置再弹窗。
     */
    fun resetGuided(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_SMS_GUIDED, false)
            .apply()
    }

    /**
     * 尝试通过 AppOpsManager 检测小米「通知类短信」权限是否已授予。
     * MIUI 将该权限映射为 AppOps 操作码 10021（OP_READ_NOTIFICATION_SMS）。
     * 若设备不支持或反射失败，返回 null 表示无法检测。
     */
    fun hasNotificationSmsPermission(context: Context): Boolean? {
        if (!isMiuiDevice()) return null
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            // MIUI 私有操作码 OP_READ_NOTIFICATION_SMS，对应 10018
            val opCode = 10018
            val checkMethod = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val result = checkMethod.invoke(
                appOps,
                opCode,
                Process.myUid(),
                context.packageName
            ) as Int
            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 如果是 MIUI 设备且从未引导过，则弹出引导对话框。
     * 用户点击"去开启"后，跳转到 MIUI 权限管理页并标记已引导。
     * 用户点击"取消"后，仅标记已引导，不再重复打扰。
     */
    fun showNotificationSmsGuideIfNeeded(activity: Activity) {
        if (!isMiuiDevice()) return
        if (hasGuided(activity)) return

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.miui_notification_sms_dialog_title))
            .setMessage(activity.getString(R.string.miui_notification_sms_dialog_message))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.miui_notification_sms_dialog_positive)) { _, _ ->
                markGuided(activity)
                openMiuiPermissionEditor(activity)
            }
            .setNegativeButton(activity.getString(R.string.miui_notification_sms_dialog_negative)) { dialog, _ ->
                markGuided(activity)
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 跳转到 MIUI 专属权限管理页（精确定位到本应用）。
     * 若跳转失败（MIUI 版本差异），则 fallback 到标准应用详情设置页。
     */
    private fun openMiuiPermissionEditor(activity: Activity) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", activity.packageName)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Fallback：跳转到标准的应用详情页
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${activity.packageName}"))
                activity.startActivity(intent)
            } catch (e2: Exception) {
                // 最终兜底，什么都做不了就忽略
            }
        }
    }
}
