package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.viewmodel.AuthUiState

@Composable
fun LoginScreen(
    authUiState: AuthUiState,
    mainSuperAdminCount: Int,
    language: AppLanguage,
    labels: LocalizedLabels,
    onToggleLanguage: () -> Unit,
    onRegisterInitialAdmin: (String, String) -> Unit,
    onRequestOtp: (String) -> Unit,
    onVerifyOtp: (String, String) -> Unit,
    onResetOtp: () -> Unit
) {
    var adminName by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "StoreVisit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "StoreVisit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier.testTag("login_language_button")
                    ) {
                        Icon(Icons.Filled.Translate, contentDescription = "Language", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(language.label)
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.img_hero_storefront),
                    contentDescription = "StoreVisit Field Operations Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                Text(
                    text = labels.welcomeBack,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (mainSuperAdminCount == 0 && authUiState.pendingOtpChallenge == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AdminPanelSettings,
                                    contentDescription = "First-Time Main Super Admin Setup",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "First-Run Setup: Main Super Admin",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "Create the Main Super Admin account to manage users, permissions, stores, and reports.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = adminName,
                                onValueChange = { adminName = it },
                                label = { Text("Full Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_admin_name_input")
                            )
                            OutlinedTextField(
                                value = mobile,
                                onValueChange = { input -> mobile = input.filter { it.isDigit() }.take(10) },
                                label = { Text(labels.mobileNumber) },
                                leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("setup_admin_mobile_input")
                            )
                            Button(
                                onClick = { onRegisterInitialAdmin(adminName, mobile) },
                                enabled = !authUiState.isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("setup_admin_submit_button")
                            ) {
                                if (authUiState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text("Create Main Super Admin & Send OTP")
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Registered Staff OTP Login",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Only registered and approved users can sign in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = mobile,
                                onValueChange = { input -> mobile = input.filter { it.isDigit() }.take(10) },
                                label = { Text(labels.mobileNumber) },
                                leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                enabled = authUiState.pendingOtpChallenge == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_mobile_input")
                            )

                            val challenge = authUiState.pendingOtpChallenge
                            if (challenge == null) {
                                Button(
                                    onClick = { onRequestOtp(mobile) },
                                    enabled = !authUiState.isLoading && mobile.length == 10,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("send_otp_button")
                                ) {
                                    if (authUiState.isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Text(labels.sendOtp)
                                    }
                                }
                            } else {
                                Surface(
                                    color = StatusInfoBg,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Device-Local OTP Verification",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = StatusInfo
                                        )
                                        Text(
                                            text = "Code sent via local notification: ${challenge.code}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = StatusInfo
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { input -> otpCode = input.filter { it.isDigit() }.take(6) },
                                    label = { Text(labels.enterOtp) },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_code_input")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            otpCode = ""
                                            onResetOtp()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Text("Change Number")
                                    }
                                    Button(
                                        onClick = { onVerifyOtp(challenge.mobileNumber, otpCode) },
                                        enabled = !authUiState.isLoading && otpCode.length == 6,
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .height(50.dp)
                                            .testTag("verify_otp_button")
                                    ) {
                                        Text(labels.verifyOtp)
                                    }
                                }
                            }
                        }
                    }
                }

                authUiState.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                authUiState.infoMessage?.let { info ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = info,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
