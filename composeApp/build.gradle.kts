import org.gradle.api.tasks.Copy
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
}

val nativeDll = rootProject.layout.projectDirectory.file("native/prebuilt/windows-x64/openyap_native.dll")

val copyNativeDll by tasks.registering(Copy::class) {
    from(nativeDll)
    into(layout.projectDirectory.dir("resources/windows-x64"))
    onlyIf { nativeDll.asFile.exists() }
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.openyap.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "OpenYap"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))
        }
    }
}

tasks.matching {
    it.name == "jvmProcessResources" ||
        it.name == "prepareAppResources" ||
        it.name == "run" ||
        it.name == "jvmRun" ||
        it.name == "runDistributable" ||
        it.name == "packageMsi" ||
        it.name == "packageDistributionForCurrentOS" ||
        it.name == "createDistributable"
}.configureEach {
    dependsOn(copyNativeDll)
}
