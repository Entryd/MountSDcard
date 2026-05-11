package com.notd.mountsdcard

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageVolume(val id: String, val rawState: String, val uuid: String) {
    fun getDisplayName(context: Context): String {
        val displayState = when (rawState) {
            "mounted" -> context.getString(R.string.state_mounted)
            "unmounted" -> context.getString(R.string.state_unmounted)
            "checking" -> context.getString(R.string.state_checking)
            "unmountable" -> context.getString(R.string.state_unmountable)
            "formatting" -> context.getString(R.string.state_formatting)
            "shared" -> context.getString(R.string.state_shared)
            else -> rawState
        }
        return "$id | $uuid ($displayState)"
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var menuVolume: AutoCompleteTextView
    private lateinit var tvStatus: TextView
    private lateinit var btnMount: Button
    private lateinit var btnHardMount: Button
    private lateinit var btnUnmount: Button
    private lateinit var btnPowerOff: Button

    private var volumesList = mutableListOf<StorageVolume>()
    private var selectedVolume: StorageVolume? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        menuVolume = findViewById(R.id.menuVolume)
        tvStatus = findViewById(R.id.tvStatus)
        btnMount = findViewById(R.id.btnMount)
        btnHardMount = findViewById(R.id.btnHardMount)
        btnUnmount = findViewById(R.id.btnUnmount)
        btnPowerOff = findViewById(R.id.btnPowerOff)

        menuVolume.setOnItemClickListener { _, _, position, _ ->
            selectedVolume = volumesList[position]
            updateButtonsState()
        }

        swipeRefresh.setOnRefreshListener { loadVolumes() }

        btnMount.setOnClickListener { executeCommand("mount") }
        btnUnmount.setOnClickListener { executeCommand("unmount") }
        btnHardMount.setOnClickListener { executeHardMount() }
        btnPowerOff.setOnClickListener { executePowerOff() }

        loadVolumes()
    }

    override fun onResume() {
        super.onResume()
        loadVolumes()
    }

    private fun loadVolumes() {
        swipeRefresh.isRefreshing = true

        CoroutineScope(Dispatchers.IO).launch {
            val result = Shell.cmd("sm list-volumes public").exec()
            val newVolumes = mutableListOf<StorageVolume>()

            if (result.isSuccess) {
                for (line in result.out) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.isNotEmpty() && parts[0].startsWith("public:")) {
                        val volId = parts[0]
                        val state = if (parts.size > 1) parts[1] else "unknown"
                        val uuid = if (parts.size > 2 && parts[2] != "null") parts[2] else getString(R.string.label_no_label)

                        newVolumes.add(StorageVolume(volId, state, uuid))
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val previouslySelectedId = selectedVolume?.id
                volumesList.clear()
                volumesList.addAll(newVolumes)

                if (volumesList.isEmpty()) {
                    tvStatus.text = getString(R.string.status_no_storage)
                    menuVolume.setAdapter(null)
                    menuVolume.setText("", false)
                    selectedVolume = null
                } else {
                    tvStatus.text = getString(R.string.status_volumes_found, volumesList.size)

                    val displayStrings = volumesList.map { it.getDisplayName(this@MainActivity) }
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, displayStrings)
                    menuVolume.setAdapter(adapter)

                    selectedVolume = volumesList.find { it.id == previouslySelectedId } ?: volumesList[0]
                    menuVolume.setText(selectedVolume?.getDisplayName(this@MainActivity), false)
                }

                updateButtonsState()
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateButtonsState() {
        if (selectedVolume == null) {
            btnMount.isEnabled = false
            btnHardMount.isEnabled = false
            btnUnmount.isEnabled = false
            btnPowerOff.isEnabled = false
            return
        }

        val state = selectedVolume?.rawState
        btnMount.isEnabled = (state != "mounted" && state != "checking")
        btnHardMount.isEnabled = (state != "mounted")
        btnUnmount.isEnabled = (state == "mounted" || state == "checking" || state == "unmountable")
        btnPowerOff.isEnabled = (state != "mounted")
    }

    private fun executePowerOff() {
        val state = selectedVolume?.rawState
        if (state == "mounted") {
            Toast.makeText(this, "Сначала отмонтируйте диск!", Toast.LENGTH_SHORT).show()
            return
        }

        btnPowerOff.isEnabled = false
        tvStatus.text = "Глубокая блокировка USB/OTG..."

        CoroutineScope(Dispatchers.IO).launch {
            val script = """
                # 1. Блокировка на уровне контроллера USB (Самый жесткий метод Linux)
                # Это запрещает ядру даже видеть то, что подключено в порт
                for d in /sys/bus/usb/devices/[0-9]*; do echo 0 > ${'$'}d/authorized 2>/dev/null; done
                
                # 2. Все известные скрытые пути питания Oplus / OnePlus
                echo 0 > /sys/class/power_supply/usb/otg_switch 2>/dev/null
                echo 0 > /sys/class/power_supply/battery/otg_switch 2>/dev/null
                echo 0 > /sys/devices/virtual/oplus_chg/usb/otg_switch 2>/dev/null
                
                # 3. Отключение OTG на уровне Android Settings 
                # (Эмулирует выключение тумблера OTG в системных настройках)
                settings put global otg_connection_state 0 2>/dev/null
                settings put system otg_state 0 2>/dev/null
                settings put secure otg_switch 0 2>/dev/null
                settings put global oplus_customize_otg_switch 0 2>/dev/null
                
                # 4. Сон для внутренних SD-карт
                for f in /sys/class/mmc_host/mmc*/device/power/control; do echo auto > ${'$'}f 2>/dev/null; done
            """.trimIndent()

            val cmdResult = Shell.cmd(script).exec()

            withContext(Dispatchers.Main) {
                if (cmdResult.isSuccess) {
                    Toast.makeText(this@MainActivity, R.string.toast_power_off, Toast.LENGTH_LONG).show()
                    tvStatus.text = "OTG аппаратно заблокирован"
                } else {
                    tvStatus.text = "Ошибка: ядро заблокировало команду"
                }
                Thread.sleep(1500)
                loadVolumes()
                btnPowerOff.isEnabled = true
            }
        }
    }

    private fun executeHardMount() {
        val volumeId = selectedVolume?.id ?: return

        btnMount.isEnabled = false
        btnHardMount.isEnabled = false
        btnUnmount.isEnabled = false
        btnPowerOff.isEnabled = false
        tvStatus.text = getString(R.string.status_hard_mounting)

        CoroutineScope(Dispatchers.IO).launch {
            val blockPath = "/dev/block/vold/$volumeId"
            val mountPoint = "/data/local/tmp/vault"

            Shell.cmd("killall fsck.exfat", "killall fsck.ntfs").exec()
            val cmdResult = Shell.cmd("mkdir -p $mountPoint", "mount $blockPath $mountPoint").exec()

            withContext(Dispatchers.Main) {
                if (cmdResult.isSuccess || cmdResult.out.joinToString("").contains("mounted")) {
                    tvStatus.text = getString(R.string.status_hard_mount_success, mountPoint)
                    Toast.makeText(this@MainActivity, R.string.toast_hard_mount_success, Toast.LENGTH_LONG).show()
                } else {
                    val error = cmdResult.err.joinToString("\n")
                    tvStatus.text = getString(R.string.status_hard_mount_error, error)
                }
                Thread.sleep(1000)
                loadVolumes()
            }
        }
    }

    private fun executeCommand(action: String) {
        val volumeId = selectedVolume?.id ?: return

        btnMount.isEnabled = false
        btnHardMount.isEnabled = false
        btnUnmount.isEnabled = false
        btnPowerOff.isEnabled = false
        tvStatus.text = getString(R.string.status_executing, action)

        CoroutineScope(Dispatchers.IO).launch {
            if (action == "unmount") {
                Shell.cmd("umount -l /data/local/tmp/vault").exec()
            }

            val cmdArgs = if (action == "unmount") "$action $volumeId force" else "$action $volumeId"
            val cmdResult = Shell.cmd("sm $cmdArgs").exec()

            withContext(Dispatchers.Main) {
                if (cmdResult.isSuccess) {
                    Toast.makeText(this@MainActivity, R.string.toast_command_sent, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, R.string.toast_command_fail, Toast.LENGTH_SHORT).show()
                }
                Thread.sleep(800)
                loadVolumes()
            }
        }
    }
}