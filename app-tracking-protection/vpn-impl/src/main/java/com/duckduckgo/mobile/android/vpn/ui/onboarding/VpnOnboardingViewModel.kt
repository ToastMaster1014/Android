/*
 * Copyright (c) 2021 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.ui.onboarding.AppThemeAppTPOnboardingResourceHelper.AppTPOnboadingResource.TRACKERS_COUNT
import com.duckduckgo.mobile.android.vpn.ui.onboarding.AppThemeAppTPOnboardingResourceHelper.AppTPOnboadingResource.TRACKING_APPS
import com.duckduckgo.mobile.android.vpn.ui.onboarding.AppThemeAppTPOnboardingResourceHelper.AppTPOnboadingResource.VPN
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class VpnOnboardingViewModel @Inject constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val vpnStore: VpnStore,
    private val vpnDetector: ExternalVpnDetector,
    private val vpnStateMonitor: VpnStateMonitor,
    private val appTPOnboardingAnimationHelper: AppTPOnboardingResourceHelper,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var lastVpnRequestTime = -1L

    data class OnboardingPage(
        val imageHeader: Int,
        val title: Int,
        val text: Int,
    )

    val pages = listOf(
        OnboardingPage(
            appTPOnboardingAnimationHelper.getHeaderRes(TRACKERS_COUNT),
            R.string.atp_OnboardingLastPageOneTitle,
            R.string.atp_OnboardingLatsPageOneSubtitle,
        ),
        OnboardingPage(
            appTPOnboardingAnimationHelper.getHeaderRes(TRACKING_APPS),
            R.string.atp_OnboardingLastPageTwoTitle,
            R.string.atp_OnboardingLastPageTwoSubTitle,
        ),
        OnboardingPage(
            appTPOnboardingAnimationHelper.getHeaderRes(VPN),
            R.string.atp_OnboardingLastPageThreeTitle,
            R.string.atp_OnboardingLastPageThreeSubTitle,
        ),
    )

    fun onTurnAppTpOffOn() {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (vpnDetector.isExternalVpnDetected()) {
                sendCommand(Command.ShowVpnConflictDialog)
            } else {
                sendCommand(Command.CheckVPNPermission)
            }
        }
    }

    fun onAppTpEnabled() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            vpnStore.onboardingDidShow()
            deviceShieldPixels.enableFromOnboarding()
        }
    }

    fun onVPNPermissionNeeded(permissionIntent: Intent) {
        lastVpnRequestTime = System.currentTimeMillis()
        sendCommand(Command.RequestVPNPermission(permissionIntent))
    }

    fun onVPNPermissionResult(resultCode: Int) {
        when (resultCode) {
            AppCompatActivity.RESULT_OK -> {
                sendCommand(Command.LaunchVPN)
                return
            }
            else -> {
                if (System.currentTimeMillis() - lastVpnRequestTime < 1000) {
                    sendCommand(Command.ShowVpnAlwaysOnConflictDialog)
                }
                lastVpnRequestTime = -1
            }
        }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
}

sealed class Command {
    object LaunchVPN : Command()
    object CheckVPNPermission : Command()
    object ShowVpnConflictDialog : Command()
    object ShowVpnAlwaysOnConflictDialog : Command()
    data class RequestVPNPermission(val vpnIntent: Intent) : Command()
}
