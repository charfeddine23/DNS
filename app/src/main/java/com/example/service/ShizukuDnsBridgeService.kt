package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

enum class ShizukuStatus {
    NOT_AVAILABLE,     // Shizuku service is not running or app is not installed
    WAITING_PERMISSION,// Shizuku service is running but permission not yet granted
    AUTHORIZED,        // Shizuku permission granted; can bridge commands
    UNSUPPORTED        // API version or device incompatible
}

data class ShizukuDnsResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)

/**
 * Service class that utilizes the Shizuku API to bridge communication between the app
 * and the system DNS settings, enabling non-root management of Private DNS configurations.
 */
class ShizukuDnsBridgeService private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuDnsBridge"
        private const val REQUEST_CODE_SHIZUKU_PERMISSION = 7001

        @Volatile
        private var instance: ShizukuDnsBridgeService? = null

        fun getInstance(context: Context): ShizukuDnsBridgeService {
            return instance ?: synchronized(this) {
                instance ?: ShizukuDnsBridgeService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.NOT_AVAILABLE)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        checkStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        _shizukuStatus.value = ShizukuStatus.NOT_AVAILABLE
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU_PERMISSION) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Shizuku permission granted by user!")
                    _shizukuStatus.value = ShizukuStatus.AUTHORIZED
                } else {
                    Log.w(TAG, "Shizuku permission denied by user")
                    _shizukuStatus.value = ShizukuStatus.WAITING_PERMISSION
                }
            }
        }

    init {
        initListeners()
        checkStatus()
    }

    private fun initListeners() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register Shizuku listeners", e)
        }
    }

    /**
     * Checks if Shizuku service is running and if the application is authorized.
     */
    fun checkStatus(): ShizukuStatus {
        val status = try {
            if (!Shizuku.pingBinder()) {
                ShizukuStatus.NOT_AVAILABLE
            } else if (Shizuku.isPreV11()) {
                ShizukuStatus.UNSUPPORTED
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.AUTHORIZED
            } else {
                ShizukuStatus.WAITING_PERMISSION
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking Shizuku status", e)
            ShizukuStatus.NOT_AVAILABLE
        }
        _shizukuStatus.value = status
        return status
    }

    /**
     * Triggers the Shizuku permission request prompt dialog to authorize this app.
     */
    fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
                } else {
                    _shizukuStatus.value = ShizukuStatus.AUTHORIZED
                }
            } else {
                Log.w(TAG, "Shizuku binder not active; cannot request permission")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error requesting Shizuku permission", e)
        }
    }

    /**
     * Directly grants WRITE_SECURE_SETTINGS to DNS Pulse without requiring a PC,
     * using the Shizuku shell bridge.
     */
    suspend fun grantWriteSecureSettingsViaShizuku(): ShizukuDnsResult = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        executePrivilegedCommand("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
    }

    /**
     * Sets the Private DNS configuration directly into the Android system settings
     * using the elevated Shizuku shell bridge.
     *
     * @param hostname The DoT hostname (e.g. "dns.adguard.com", "one.one.one.one"),
     *                 or null/empty to revert to Opportunistic (Auto) mode.
     */
    suspend fun setPrivateDns(hostname: String?): ShizukuDnsResult = withContext(Dispatchers.IO) {
        if (_shizukuStatus.value != ShizukuStatus.AUTHORIZED && checkStatus() != ShizukuStatus.AUTHORIZED) {
            return@withContext ShizukuDnsResult(
                success = false,
                output = "",
                error = "Shizuku is not running or not authorized. Current status: ${_shizukuStatus.value}"
            )
        }

        val commands = if (hostname.isNullOrBlank()) {
            // Opportunistic / Auto
            "settings put global private_dns_mode opportunistic && settings put global private_dns_specifier \"\""
        } else {
            // Strict hostname mode
            "settings put global private_dns_mode hostname && settings put global private_dns_specifier $hostname"
        }

        val result = executePrivilegedCommand(commands)

        // Also ensure WRITE_SECURE_SETTINGS is permanently granted to app for seamless zero-latency in-process edits
        if (result.success) {
            executePrivilegedCommand("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        }

        result
    }

    /**
     * Completely disables Private DNS in system settings via Shizuku.
     */
    suspend fun disablePrivateDns(): ShizukuDnsResult = withContext(Dispatchers.IO) {
        val cmd = "settings put global private_dns_mode off && settings put global private_dns_specifier \"\""
        executePrivilegedCommand(cmd)
    }

    /**
     * Reads the current system Private DNS specifier directly via shell.
     */
    suspend fun readSystemPrivateDns(): String = withContext(Dispatchers.IO) {
        val modeResult = executePrivilegedCommand("settings get global private_dns_mode")
        val hostResult = executePrivilegedCommand("settings get global private_dns_specifier")
        "Mode: ${modeResult.output.trim()} | Specifier: ${hostResult.output.trim()}"
    }

    /**
     * Executes an elevated shell command using Shizuku's remote process interface.
     */
    private suspend fun executePrivilegedCommand(command: String): ShizukuDnsResult = withContext(Dispatchers.IO) {
        try {
            // Invoke Shizuku.newProcess via reflection or direct API to execute with shell/UID 2000 context
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val cmdArray = arrayOf("sh", "-c", command)
            val process = newProcessMethod.invoke(null, cmdArray, null, null) as Process

            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    outputBuilder.appendLine(line)
                }
            }

            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    errorBuilder.appendLine(line)
                }
            }

            val exitCode = process.waitFor()
            val output = outputBuilder.toString().trim()
            val error = errorBuilder.toString().trim()

            if (exitCode == 0) {
                Log.i(TAG, "Shizuku command succeeded: $command -> $output")
                ShizukuDnsResult(success = true, output = output)
            } else {
                Log.e(TAG, "Shizuku command failed ($exitCode): $command -> $error")
                ShizukuDnsResult(success = false, output = output, error = error.ifBlank { "Exit code $exitCode" })
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception executing Shizuku privileged command: $command", e)
            ShizukuDnsResult(success = false, output = "", error = e.localizedMessage ?: "Unknown execution error")
        }
    }

    fun cleanUp() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Error cleaning up Shizuku listeners", e)
        }
    }
}
