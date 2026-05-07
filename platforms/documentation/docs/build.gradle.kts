import gradlebuild.basics.configurationCacheEnabledForDocsTests
import gradlebuild.basics.repoRoot
import gradlebuild.basics.runBrokenForConfigurationCacheDocsTests
import gradlebuild.basics.util.getSingleFileProvider
import gradlebuild.integrationtests.androidhomewarmup.SdkVersion
import gradlebuild.integrationtests.configureTestSourceSetInIde
import gradlebuild.integrationtests.model.GradleDistribution
import java.io.FileFilter
import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.gradle.docs.internal.tasks.CheckLinks
import org.gradle.docs.samples.internal.tasks.InstallSample
import org.gradle.internal.os.OperatingSystem

plugins {
    id("java-library") // Needed for the dependency-analysis plugin. However, we should not need this. This is not a real library.
    id("gradlebuild.internal.java")
    // TODO: Apply asciidoctor in documentation plugin instead.
    id("org.asciidoctor.jvm.convert")
    id("gradlebuild.documentation")
    id("org.gradle.samples")
    id("gradlebuild.android-home-warmup")
}

configureTestSourceSetInIde(sourceSets.docsTest.get())

androidHomeWarmup {
    rootProjectDir = project.layout.projectDirectory.dir("../../..")
    sdkVersions.set(
        listOf(
            // Used by declaringConfigurations-android and declaringConfigurations-kmp (AGP 9.0.1)
            SdkVersion(compileSdk = 36, buildTools = "36.1.0", agpVersion = "9.0.1"),

            // Used by structuring-software-projects/android-app snippet (AGP 8.9.0)
            SdkVersion(compileSdk = 28, buildTools = "35.0.0", agpVersion = "8.9.0"),
        ),
    )
}

configurations {
    consumable("gradleFullDocsElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-documentation"))
        }
    }
}

configurations {
    named("docsTestRuntimeClasspath") {
        extendsFrom(configurations.getByName("integTestDistributionRuntimeOnly"))
    }
}

configurations.docsTestImplementation {
    // The 'org.gradle.samples' plugin from the old gradle/guides build pulls in slf4j-simple, which we don't want.
    // See: https://github.com/gradle/guides/blob/ba018cec535d90f75876bfcca29381d213a956cc/subprojects/gradle-guides-plugin/src/main/java/org/gradle/docs/samples/internal/SamplesDocumentationPlugin.java#L335
    exclude("org.slf4j", "slf4j-simple")
}

dependencyAnalysis {
    issues {
        ignoreSourceSet(sourceSets.docsTest.name)
    }
}

dependencies {
    // generate Javadoc for the full Gradle distribution
    runtimeOnly(project(":distributions-full"))

    userGuideTask(buildLibs.xalan)
    userGuideTask(buildLibs.xerces)
    userGuideTask(buildLibs.xslthl)

    userGuideStyleSheets(variantOf(buildLibs.docbook) { classifier("resources"); artifactType("zip") })

    testImplementation(project(":base-services"))
    testImplementation(project(":core"))
    testImplementation(libs.jsoup)
    testImplementation(testLibs.selenium)
    testImplementation(libs.commonsHttpclient)
    testImplementation(testLibs.httpmime)

    docsTestImplementation(platform(project(":distributions-dependencies")))
    docsTestImplementation(project(":internal-integ-testing"))
    docsTestImplementation(project(":base-services"))
    docsTestImplementation(project(":logging"))
    docsTestImplementation(testLibs.junit)
    docsTestRuntimeOnly(testLibs.junitPlatform)

    integTestDistributionRuntimeOnly(project(":distributions-full"))

    constraints {
        testImplementation(testLibs.jettyWebsocket)
    }
}

jvmCompile {
    addCompilationFrom(sourceSets.docsTest)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

asciidoctorj {
    setVersion("2.5.13")
    modules.pdf.setVersion("2.3.10")
    // TODO: gif are not supported in pdfs, see also https://github.com/gradle/gradle/issues/24193
    // TODO: tables are not handled properly in pdfs
    fatalWarnings.add(
        Regex("^(?!GIF image format not supported|dropping cells from incomplete row detected end of table|.*Asciidoctor PDF does not support table cell content that exceeds the height of a single page).*").toPattern()
    )
}

tasks.withType<AsciidoctorTask>().configureEach {
    val doctorj = extensions.getByType<org.asciidoctor.gradle.jvm.AsciidoctorJExtension>()
    doctorj.docExtensions(
        project.dependencies.create(project(":docs-asciidoctor-extensions")),
        project.dependencies.create(files("src/main/resources"))
    )
}

gradleDocumentation {
    javadocs {
        val jvmVersion = jvmCompile.compilations.named("main").flatMap { it.targetJvmVersion }
        javaApi = jvmVersion.map { v -> uri("https://docs.oracle.com/en/java/javase/$v/docs/api/") }
        javaPackageListLoc = jvmVersion.map { v -> project.layout.projectDirectory.dir("src/docs/javaPackageList/$v/") }
        groovyApi = libs.versions.groovy.map { v -> project.uri("https://docs.groovy-lang.org/docs/groovy-$v/html/gapi") }
        groovyPackageListSrc = libs.versions.groovy.map { v -> "org.apache.groovy:groovy-all:$v:groovydoc" }
    }
}

tasks.named<Sync>("stageDocs") {
    // Add samples to generated documentation
    from(samples.distribution.renderedDocumentation) {
        into("samples")
    }
}

samples {
    // TODO: Do this lazily so we don't need to walk the filesystem during configuration
    // iterate through each snippets and record their names and locations
    val directoriesOnly = FileFilter { it.isDirectory }
    val snippetsRoot = file("src/snippets")
    val variantDirNames = setOf("groovy", "kotlin", "common", "tests", "tests-groovy", "tests-kotlin", "tests-common")

    // Recursively find snippet directories (those containing a groovy/ or kotlin/ variant subdirectory)
    val snippetDirs = mutableListOf<File>()
    fun findSnippets(dir: File) {
        for (child in dir.listFiles(directoriesOnly).orEmpty()) {
            if (child.name in variantDirNames || child.name == "integration-tests" || child.name == "unused") continue
            if (File(child, "kotlin").exists() || File(child, "groovy").exists()) {
                snippetDirs.add(child)
            } else {
                findSnippets(child)
            }
        }
    }
    findSnippets(snippetsRoot)

    snippetDirs.forEach { snippetDir ->
        val relativePath = snippetsRoot.toPath().relativize(snippetDir.toPath()).toString()
        val id = org.gradle.docs.internal.StringUtils.toLowerCamelCase("snippet-${relativePath.replace(File.separatorChar, '-')}")
        publishedSamples.create(id) {
            description = "Snippet from $snippetDir"
            category = "Other"
            readmeFile = file("src/snippets/default-readme.adoc")
            sampleDirectory = snippetDir
            promoted = false
        }
    }
}

// Use the version of Gradle being built, not the version of Gradle used to build,
// also don't validate distribution url, since it is just a local distribution
tasks.named<Wrapper>("generateWrapperForSamples") {
    gradleVersion = project.version.toString()
    validateDistributionUrl = false
}

// TODO: The rich console to plain text is flaky
tasks.named("checkAsciidoctorSampleContents") {
    enabled = false
}

// exclude (unused and non-existing) wrapper of development Gradle version, as well as README, because the timestamp in the Gradle version break the cache
tasks.withType<InstallSample>().configureEach {
    if (name.contains("ForTest")) {
        excludes.add("gradle/wrapper/**")
        excludes.add("README")
    }
}

tasks.named("quickTest") {
    dependsOn("checkDeadInternalLinks")
}

// TODO add some kind of test precondition support in sample test conf
tasks.named<Test>("docsTest") {
    useJUnitPlatform()

    dependsOn("androidHomeWarmup")

    // The org.gradle.samples plugin uses Exemplar to execute integration tests on the samples.
    // Exemplar doesn't know about that it's running in the context of the gradle/gradle build
    // so it uses the Gradle distribution from the running build. This is not correct, because
    // we want to verify that the samples work with the Gradle distribution being built.
    val installationEnvProvider = objects.newInstance<GradleInstallationForTestEnvironmentProvider>().apply {
        gradleDistribution.homeDir.fileProvider(configurations.integTestDistributionRuntimeClasspath.getSingleFileProvider())
        samplesdir = project.layout.buildDirectory.dir("working/samples/testing")
        repoRoot = project.repoRoot()
    }
    jvmArgumentProviders.add(installationEnvProvider)

    // For unknown reason, this is set to 'sourceSet.getRuntimeClasspath()' in the 'org.gradle.samples' plugin
    testClassesDirs = sourceSets.docsTest.get().output.classesDirs
    // 'integTest.samplesdir' is set to an absolute path by the 'org.gradle.samples' plugin
    systemProperties.clear()

    filter {
        if (!javaVersion.isJava11Compatible) {
            // This test sets source and target compatibility to 11
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-reference-dsl-apis-accessors_*")
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_12)) {
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-reference-other-topics-gradle-version_*_testKitFunctionalTestSpockGradleDistribution")
        }

        if (!javaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
            // AGP AND KMP are tested on Java 17 only
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-reference-dependency-management-declaring-dependencies-declaring-configurations-*")
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_25)) {
            // Kotlin does not yet support 25 JDK target
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-best-practices-kotlin-std-lib*")
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-best-practices-use-convention-plugins-do_kotlin*")
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_26)) {
            // PMD doesn't support Java 26
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-reference-core-plugins-code-quality*")
            // There is a bug in either AGP or the JDK which causes JdkImageTransform to fail with Java 26
            // https://issuetracker.google.com/issues/486844145
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-reference-dependency-management-declaring-dependencies-declaring-configurations-kmp*")
        }

        if (OperatingSystem.current().isMacOsX && System.getProperty("os.arch") == "aarch64") {
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-reference-platforms-native-*")
        }
    }

    filter {
        // TODO(https://github.com/gradle/gradle/issues/22538)
        excludeTestsMatching("org.gradle.docs.samples.*.snippet-reference-platforms-jvm-cross-compilation_*_crossCompilation")
    }

    if (project.configurationCacheEnabledForDocsTests) {
        systemProperty("org.gradle.integtest.samples.cleanConfigurationCacheOutput", "true")
        systemProperty("org.gradle.integtest.executer", "configCache")

        filter {
            // Configuration cache samples enable configuration cache explicitly. We're not going to run them with the configuration cache executer.
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-optimizing-builds-configuration-cache-*")
            excludeTestsMatching("*WithoutCC*")

            // These tests cover features that are not planned to be supported in the first stable release of the configuration cache.
            val testsForUnsupportedFeatures = listOf(
                "snippet-reference-other-topics-add-behaviour-to-ant-target_groovy_addBehaviourToAntTarget",
                "snippet-reference-other-topics-add-behaviour-to-ant-target_kotlin_addBehaviourToAntTarget",
                "snippet-reference-other-topics-depends-on-ant-target_groovy_dependsOnAntTarget",
                "snippet-reference-other-topics-depends-on-ant-target_kotlin_dependsOnAntTarget",
                "snippet-reference-other-topics-depends-on-task_groovy_dependsOnTask",
                "snippet-reference-other-topics-depends-on-task_kotlin_dependsOnTask",
                "snippet-reference-other-topics-hello_groovy_antHello",
                "snippet-reference-other-topics-hello_kotlin_antHello",
                "snippet-reference-other-topics-rename-task_groovy_renameAntDelegate",
                "snippet-reference-other-topics-rename-task_kotlin_renameAntDelegate",
                "snippet-reference-other-topics-use-external-ant-task-with-config_groovy_useExternalAntTaskWithConfig",
                "snippet-reference-other-topics-use-external-ant-task-with-config_kotlin_useExternalAntTaskWithConfig",
                "snippet-reference-other-topics-ant-logging_groovy_antLogging",
                "snippet-reference-other-topics-ant-logging_kotlin_antLogging",
                "snippet-reference-runtime-configuration-custom-logger_groovy_customLogger.groovy",
                "snippet-reference-runtime-configuration-custom-logger_kotlin_customLogger.kotlin",
                "snippet-reference-platforms-native-cpp_groovy_nativeComponentReport",
                "snippet-reference-platforms-native-cunit_groovy_assembleDependentComponents",
                "snippet-reference-platforms-native-cunit_groovy_assembleDependentComponentsReport",
                "snippet-reference-platforms-native-cunit_groovy_buildDependentComponents",
                "snippet-reference-platforms-native-cunit_groovy_buildDependentComponentsReport",
                "snippet-reference-platforms-native-cunit_groovy_completeCUnitExample",
                "snippet-reference-platforms-native-cunit_groovy_dependentComponentsReport",
                "snippet-reference-platforms-native-cunit_groovy_dependentComponentsReportAll",
            )

            // These tests use third-party plugins at versions that may not support the configuration cache properly.
            // The tests should be removed from this list when the plugin is updated to the version that works with the configuration cache properly.
            val testsWithThirdPartyFailures = listOf<String>(
            )

            // These tests cover features that the configuration cache doesn't support yet, but we plan to do that before hitting stable.
            // The tests should be removed from this list when the feature becomes supported.
            val testsForNotYetSupportedFeatures = listOf(
                // TODO(https://github.com/gradle/gradle/issues/22879) The snippet extracts build logic into a method and calls the method at execution time
                "snippet-tutorial-ant-loadfile-with-method_groovy_antLoadfileWithMethod",
                "snippet-tutorial-ant-loadfile-with-method_kotlin_antLoadfileWithMethod",
            )

            // Tests that can and has to be fixed to run with the configuration cache enabled.
            // Set the Gradle property runBrokenConfigurationCacheDocsTests=true to run tests from this list or any of the lists above.
            val testsToBeFixedForConfigurationCache = listOf(
                "snippet-optimizing-builds-build-cache-configure-task_groovy_configureTask",
                "snippet-optimizing-builds-build-cache-configure-task_kotlin_configureTask",
            )

            val brokenTests = testsForUnsupportedFeatures + testsWithThirdPartyFailures + testsForNotYetSupportedFeatures + testsToBeFixedForConfigurationCache
            brokenTests.forEach { testName ->
                val testMask = "org.gradle.docs.samples.*.$testName"
                if (project.runBrokenForConfigurationCacheDocsTests) {
                    includeTestsMatching(testMask)
                } else {
                    excludeTestsMatching(testMask)
                }
            }
        }
    } else {
        filter {
            excludeTestsMatching("*WithCC*")
        }
    }
}

// Publications for the docs subproject:

configurations {
    named("gradleFullDocsElements") {
        // TODO: This breaks the provider
        outgoing.artifact(project.gradleDocumentation.documentationRenderedRoot.get().asFile) {
            builtBy(tasks.named("docs"))
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("checkstyleApi"))
}

// TODO there is some duplication with DistributionTest.kt here - https://github.com/gradle/gradle-private/issues/3126
abstract class GradleInstallationForTestEnvironmentProvider : CommandLineArgumentProvider {

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val samplesdir: DirectoryProperty

    @get:Nested
    abstract val gradleDistribution: GradleDistribution

    @get:Internal
    abstract val repoRoot: DirectoryProperty

    override fun asArguments(): Iterable<String> {
        return listOf(
            "-DintegTest.gradleHomeDir=${gradleDistribution.homeDir.get().asFile}",
            "-DintegTest.samplesdir=${samplesdir.get().asFile}",
            "-DintegTest.gradleUserHomeDir=${repoRoot.dir("intTestHomeDir/${gradleDistribution.name.get()}").get().asFile}"
        )
    }
}

tasks.withType<CheckLinks>().configureEach {
    enabled = !gradle.startParameter.taskNames.contains("docs:docsTest")
}

tasks.register("checkLinks") {
    dependsOn(tasks.withType<CheckLinks>())
}

errorprone {
    nullawayEnabled = true
}
