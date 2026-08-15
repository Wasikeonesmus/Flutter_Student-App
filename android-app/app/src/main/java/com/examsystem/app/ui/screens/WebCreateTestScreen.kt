package com.examsystem.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.examsystem.app.ui.theme.RedPrimary
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

private const val WEB_DASHBOARD_URL = "https://examapp-57718.web.app"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebCreateTestScreen(
    editTestId: String? = null,
    onTestCreated: () -> Unit,
    onBack: () -> Unit
) {
    var webUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingToken by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(editTestId) {
        // Fetch custom token for SSO
        Firebase.functions.getHttpsCallable("getCustomToken")
            .call()
            .addOnSuccessListener { result ->
                val data = result.data as? Map<String, Any>
                val token = data?.get("token") as? String
                val basePath = if (editTestId.isNullOrBlank()) {
                    "$WEB_DASHBOARD_URL/create-test"
                } else {
                    "$WEB_DASHBOARD_URL/edit-test/$editTestId"
                }
                webUrl = if (token != null) {
                    "$basePath?token=$token"
                } else {
                    basePath
                }
                isLoadingToken = false
            }
            .addOnFailureListener { e ->
                // Fallback to loading URL without token (instructor can log in manually inside WebView)
                val basePath = if (editTestId.isNullOrBlank()) {
                    "$WEB_DASHBOARD_URL/create-test"
                } else {
                    "$WEB_DASHBOARD_URL/edit-test/$editTestId"
                }
                webUrl = basePath
                isLoadingToken = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editTestId != null) "Edit Test (Web)" else "Create Test (Web)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RedPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            if (isLoadingToken) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = RedPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text("Setting up secure web session...", color = Color.Gray)
                }
            } else {
                webUrl?.let { url ->
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        // When successfully redirected back to the exams listing, return to native dashboard/list
                                        if (url != null && url.contains("/exams")) {
                                            onTestCreated()
                                        }
                                    }
                                }
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
