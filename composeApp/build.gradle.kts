import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

fun File.asJdkHomeCandidate(): File =
    if (name.equals("bin", ignoreCase = true)) parentFile ?: this else this

fun File.hasJPackage(): Boolean = resolve("bin/jpackage.exe").exists() || resolve("bin/jpackage").exists()

fun resolvePackagingJavaHome(): String {
    val explicitCandidates = listOfNotNull(
        System.getenv("JPACKAGE_JAVA_HOME"),
        System.getenv("JAVA_HOME"),
        System.getenv("JDK_HOME"),
        System.getProperty("java.home"),
    )
        .map(::File)
        .map { it.asJdkHomeCandidate() }

    val pathCandidates = System.getenv("PATH")
        ?.split(File.pathSeparator)
        .orEmpty()
        .asSequence()
        .map(::File)
        .filter { it.exists() }
        .map { it.asJdkHomeCandidate() }

    return (explicitCandidates.asSequence() + pathCandidates)
        .map { it.absoluteFile }
        .distinctBy { it.path.lowercase() }
        .firstOrNull { it.hasJPackage() }
        ?.absolutePath
        ?: error(
            "No full JDK with jpackage was found. Set JPACKAGE_JAVA_HOME or JAVA_HOME to a JDK 21+ installation."
        )
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinx.serialization)
}

val nativeDll =
    rootProject.layout.projectDirectory.file("native/prebuilt/windows-x64/openyap_native.dll")

val copyNativeDll by tasks.registering(Copy::class) {
    from(nativeDll)
    into(layout.projectDirectory.dir("resources/windows-x64"))
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
        javaHome = resolvePackagingJavaHome()

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "OpenYap"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(layout.projectDirectory.dir("resources"))

            windows {
                iconFile.set(project.file("icons/openyap.ico"))
                shortcut = true
                menuGroup = "OpenYap"
                upgradeUuid = "a6b7d58d-31be-42ce-a52f-0a8e5c7b8bf1"
            }
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

gradle.projectsEvaluated {
    tasks.findByName("jvmRun")?.let { task ->
        task.dependsOn("run")
        task.actions.clear()
    }
}
