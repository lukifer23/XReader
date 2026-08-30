import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

abstract class CheckNoConflictCopyFiles : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val conflicts = sourceFiles.files
            .filter { it.isFile }
            .filter { CONFLICT_COPY_PATTERN.matches(it.name) }
            .map { it.path }
            .sorted()

        if (conflicts.isNotEmpty()) {
            throw GradleException(
                "Conflict-copy source files are not allowed:\n" + conflicts.joinToString(separator = "\n"),
            )
        }
    }

    companion object {
        private val CONFLICT_COPY_PATTERN = Regex(""".+ [0-9]+\..+""")
    }
}

abstract class DeleteGeneratedConflictCopyFiles : DefaultTask() {
    @get:Internal
    abstract val buildDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun deleteConflicts() {
        val root = buildDirectory.asFile.get()
        if (!root.exists()) return
        root.walkTopDown()
            .filter { it.isFile && CONFLICT_COPY_PATTERN.matches(it.name) }
            .forEach { file ->
                if (!file.delete()) {
                    throw GradleException("Could not delete generated conflict-copy file before build: ${file.path}")
                }
            }
    }

    companion object {
        private val CONFLICT_COPY_PATTERN = Regex(""".+ [0-9]+\..+""")
    }
}

abstract class VerifyReleasePackaging : DefaultTask() {
    @get:InputFile
    abstract val apkFile: RegularFileProperty

    @get:Input
    abstract val maximumBytes: Property<Long>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val apk = apkFile.asFile.get()
        val ceiling = maximumBytes.get()
        if (!apk.isFile) throw GradleException("Release APK was not produced: ${apk.path}")
        if (apk.length() > ceiling) {
            throw GradleException("Release APK is ${apk.length()} bytes; ceiling is $ceiling bytes (210 MiB).")
        }

        val allowedDuplicatedQnnBasenames = setOf(
            "libQnnHtpV68.so", "libQnnHtpV68Skel.so",
            "libQnnHtpV69.so", "libQnnHtpV69Skel.so",
            "libQnnHtpV73.so", "libQnnHtpV73Skel.so",
            "libQnnHtpV75.so", "libQnnHtpV75Skel.so",
            "libQnnHtpV79.so", "libQnnHtpV79Skel.so",
            "libQnnHtpV81.so", "libQnnHtpV81Skel.so",
            "libQnnSystem.so",
        )
        val prohibitedFallbackBasenames = setOf(
            "libOpenCL.so",
            "libOpenCL_adreno.so",
            "libQnnGpu.so",
            "libQnnGpuNetRunExtensions.so",
        )

        ZipFile(apk).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            val prohibited = entries.filter { it.name.substringAfterLast('/') in prohibitedFallbackBasenames }
            if (prohibited.isNotEmpty()) {
                throw GradleException("Strict-QNN release contains prohibited fallback runtimes: ${prohibited.joinToString { it.name }}")
            }

            val nativeBasenames = entries
                .filter { it.name.startsWith("lib/arm64-v8a/") }
                .map { it.name.substringAfterLast('/') }
                .toSet()
            val duplicatedQnn = entries
                .filter { it.name.startsWith("assets/qnn-dsp/") }
                .filter { it.name.substringAfterLast('/') in nativeBasenames }
            val unexpected = duplicatedQnn.filter { it.name.substringAfterLast('/') !in allowedDuplicatedQnnBasenames }
            if (unexpected.isNotEmpty()) {
                throw GradleException("Unexpected duplicated QNN runtime entries: ${unexpected.joinToString { it.name }}")
            }

            val report = reportFile.asFile.get()
            report.parentFile.mkdirs()
            report.writeText(
                buildString {
                    appendLine("apk=${apk.path}")
                    appendLine("apkBytes=${apk.length()}")
                    appendLine("ceilingBytes=$ceiling")
                    appendLine("qnnDuplicateAllowlist=${allowedDuplicatedQnnBasenames.sorted().joinToString()}")
                    appendLine("qnnDuplicatedEntries=${duplicatedQnn.map { it.name }.sorted().joinToString()}")
                    appendLine("largestEntries:")
                    entries.sortedByDescending { it.size }.take(25).forEach { appendLine("${it.size}\t${it.name}") }
                },
            )
            logger.lifecycle("Release packaging verified: ${apk.length()} bytes; report: ${report.path}")
        }
    }
}

android {
    namespace = "com.xreader.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xreader.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
        resources {
            pickFirsts += "assets/com/tom_roush/fontbox/resources/cmap/**"
        }
    }

    lint {
        lintConfig = file("lint.xml")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(21)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val cleanGeneratedConflictCopies by tasks.registering(DeleteGeneratedConflictCopyFiles::class) {
    group = "build"
    description = "Deletes macOS conflict-copy files from generated Android outputs before packaging."
    buildDirectory.set(layout.buildDirectory)
}

val cleanGeneratedConflictCopiesBeforeDex by tasks.registering(DeleteGeneratedConflictCopyFiles::class) {
    group = "build"
    description = "Deletes generated conflict-copy class files after Kotlin compilation and before dexing."
    buildDirectory.set(layout.buildDirectory)
}

val cleanGeneratedConflictCopiesBeforeDexMerge by tasks.registering(DeleteGeneratedConflictCopyFiles::class) {
    group = "build"
    description = "Deletes generated conflict-copy dex archives immediately before dex merging."
    buildDirectory.set(layout.buildDirectory)
}

val checkNoSourceConflictCopies by tasks.registering(CheckNoConflictCopyFiles::class) {
    group = "verification"
    description = "Fails when conflict-copy files exist in source inputs that would break Android resource/native packaging."
    sourceFiles.from(
        fileTree("src") { include("**/*") },
        fileTree("schemas") { include("**/*") },
    )
}

val verifyReleasePackaging by tasks.registering(VerifyReleasePackaging::class) {
    group = "verification"
    description = "Verifies the assembled release APK size and strict-QNN runtime inventory."
    dependsOn("assembleRelease")
    apkFile.set(layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk"))
    maximumBytes.set(210L * 1024L * 1024L)
    reportFile.set(layout.buildDirectory.file("reports/release-packaging.txt"))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(cleanGeneratedConflictCopies, checkNoSourceConflictCopies)
}

cleanGeneratedConflictCopiesBeforeDex.configure {
    mustRunAfter(tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") })
}

tasks.matching { it.name.startsWith("dexBuilder") }.configureEach {
    dependsOn(cleanGeneratedConflictCopiesBeforeDex)
}

cleanGeneratedConflictCopiesBeforeDexMerge.configure {
    mustRunAfter(tasks.matching { it.name.startsWith("dexBuilder") })
}

tasks.matching { it.name.startsWith("merge") && it.name.contains("Dex") }.configureEach {
    dependsOn(cleanGeneratedConflictCopiesBeforeDexMerge)
}

tasks.matching {
    it.name.contains("NativeLibs") ||
        it.name.startsWith("strip") && it.name.endsWith("DebugSymbols") ||
        it.name.startsWith("package")
}.configureEach {
    dependsOn(cleanGeneratedConflictCopies)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.05.01"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.commons:commons-compress:1.28.0")

    implementation("org.readium.kotlin-toolkit:readium-shared:3.2.0")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.2.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.2.0")
    implementation("org.readium.kotlin-toolkit:readium-adapter-pdfium:3.2.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
