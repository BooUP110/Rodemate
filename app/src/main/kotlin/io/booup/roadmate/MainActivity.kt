package io.booup.roadmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Intent

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: RoadmateViewModel
    private val vehiclePermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        lifecycleLog("permissionResult=$result")
        viewModel.refreshVehiclePermissions()
    }
    private fun lifecycleLog(event: String) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val process = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(process)
        android.util.Log.i("RoadmateLifecycle", "$event pid=${android.os.Process.myPid()} importance=${process.importance} finishing=$isFinishing changingConfig=$isChangingConfigurations")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RoadmateSounds.init(applicationContext)
        lifecycleLog("onCreate")
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(RoadmateViewModel::class.java))
                return requireNotNull(modelClass.cast(RoadmateViewModel { scope ->
                    RuntimeBindings.create(applicationContext, scope, AndroidContextClock(), noBackupFilesDir)
                }))
            }
        })[RoadmateViewModel::class.java]
        viewModel.onLaunch?.invoke(intent)
        setContent { RuntimeScreen(viewModel) }
        val missing = RuntimeBindings.runtimePermissions.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (savedInstanceState == null && missing.isNotEmpty()) vehiclePermissionRequest.launch(missing.toTypedArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.onLaunch?.invoke(intent)
    }

    override fun onStart() {
        super.onStart()
        lifecycleLog("onStart")
        viewModel.foreground()
    }

    override fun onStop() {
        lifecycleLog("onStop")
        viewModel.background()
        super.onStop()
    }
    override fun onResume() { super.onResume(); lifecycleLog("onResume") }
    override fun onPause() { lifecycleLog("onPause"); super.onPause() }
    override fun onDestroy() { lifecycleLog("onDestroy"); super.onDestroy() }
}
