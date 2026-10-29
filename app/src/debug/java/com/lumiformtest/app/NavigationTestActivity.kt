package com.lumiformtest.app

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

// An empty Hilt host lets the device test set content without changing the launcher.
@AndroidEntryPoint
class NavigationTestActivity : ComponentActivity()
