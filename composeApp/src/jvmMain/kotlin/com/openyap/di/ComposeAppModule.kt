package com.openyap.di

import com.openyap.platform.ComposeOverlayController
import com.openyap.platform.OverlayController
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class ComposeAppModule {

    @Single
    fun provideComposeOverlayController(): ComposeOverlayController = ComposeOverlayController()

    @Single
    fun provideOverlayController(impl: ComposeOverlayController): OverlayController = impl
}
