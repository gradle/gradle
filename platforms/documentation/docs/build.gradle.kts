import gradlebuild.basics.configurationCacheEnabledForDocsTests
import gradlebuild.basics.googleApisJs
import gradlebuild.basics.repoRoot
import gradlebuild.basics.runBrokenForConfigurationCacheDocsTests
import gradlebuild.basics.util.getSingleFileProvider
import gradlebuild.integrationtests.model.GradleDistribution
import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.gradle.docs.internal.tasks.CheckLinks
import org.gradle.docs.samples.internal.tasks.InstallSample
import org.gradle.internal.os.OperatingSystem
import java.io.FileFilter

plugins {
    id("java-library") // Needed for the dependency-analysis plugin. However, we should not need this. This is not a real library.
    id("gradlebuild.jvm-library")
    // TODO: Apply asciidoctor in documentation plugin instead.
    id("org.asciidoctor.jvm.convert")
    id("gradlebuild.documentation")
    id("gradlebuild.generate-samples")
}

repositories {
    googleApisJs()
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
    // The 'gradlebuild.generate-samples' plugin uses the 'org.gradle.samples' plugin from the old gradle/guides build, which pulls in slf4j-simple, which we don't want.
    // Because this is done directly by the plugin application logic, we can't use a ComponentMetadataRule to exclude it.
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

    userGuideTask("xalan:xalan:2.7.1")
    userGuideTask("xerces:xercesImpl:2.11.0")
    userGuideTask("net.sf.xslthl:xslthl:2.0.1")

    userGuideStyleSheets("net.sf.docbook:docbook-xsl:1.75.2:resources@zip")

    jquery("jquery:jquery.min:3.5.1@js")

    testImplementation(project(":base-services"))
    testImplementation(project(":core"))
    testImplementation(libs.jsoup)
    testImplementation("org.seleniumhq.selenium:selenium-htmlunit-driver:2.42.2")
    testImplementation(libs.commonsHttpclient)
    testImplementation(libs.httpmime)

    docsTestImplementation(platform(project(":distributions-dependencies")))
    docsTestImplementation(project(":internal-integ-testing"))
    docsTestImplementation(project(":base-services"))
    docsTestImplementation(project(":logging"))
    docsTestImplementation(libs.junit)
    docsTestRuntimeOnly(libs.junitPlatform)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

jvmCompile {
    compilations {
        named("main") {
            targetJvmVersion = 17
        }
    }
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
        groovyApi = project.uri("https://docs.groovy-lang.org/docs/groovy-${libs.groovyVersion}/html/gapi")
        groovyPackageListSrc = "org.apache.groovy:groovy-all:${libs.groovyVersion}:groovydoc"
    }
}

tasks.named<Sync>("stageDocs") {
    // Add samples to generated documentation
    from(samples.distribution.renderedDocumentation) {
        into("samples")
    }
}

samples {
    templates {
        val javaAndroidApplication by creating
        val structuringSoftwareProjects by creating
        val springBootWebApplication by creating {
            target = "app"
        }
        val gradlePluginInJava by creating {
            target = "greeting-plugin"
        }
        val gradlePluginInJavaInBuildSrc by creating {
            sourceDirectory = gradlePluginInJava.sourceDirectory
            target = "buildSrc"
        }
        val buildSrcPluginJavaModuleTransform by creating

        val javaApplication by creating
        val javaListLibrary by creating
        val javaUtilitiesLibrary by creating
        val javaListLibraryInMyLibrary by creating {
            sourceDirectory = javaListLibrary.sourceDirectory
            target = "my-library"
        }
        val javaUtilitiesLibraryInMyLibrary by creating {
            sourceDirectory = javaUtilitiesLibrary.sourceDirectory
            target = "my-library"
        }
        val javaApplicationAsSubproject by creating {
            sourceDirectory = javaApplication.sourceDirectory
            target = "application"
        }
        val javaListLibraryAsSubproject by creating {
            sourceDirectory = javaListLibrary.sourceDirectory
            target = "list"
        }
        val javaUtilitiesLibraryAsSubproject by creating {
            sourceDirectory = javaUtilitiesLibrary.sourceDirectory
            target = "utilities"
        }

        val javaJunit5TestForApplication by creating {
            target = "application"
        }
        val javaJunit5TestForListLibrary by creating {
            target = "list"
        }
        val javaJunit5TestForUtilitiesLibrary by creating {
            target = "utilities"
        }
        val javaJunit5IntegrationTestForApplication by creating {
            target = "application"
        }
        val javaJunit5IntegrationTestForUtilitiesLibrary by creating {
            target = "utilities"
        }

        val javaModuleInfoForListLibrary by creating {
            target = "list"
        }
        val javaModuleInfoForUtilitiesLibrary by creating {
            target = "utilities"
        }
        val javaModuleInfoForApplication by creating {
            target = "application"
        }
        val javaJunit5ModuleInfoForUtilitiesLibrary by creating {
            target = "utilities"
        }
        val javaJunit5ModuleInfoForApplication by creating {
            target = "application"
        }

        val groovyListLibrary by creating
        val groovyUtilitiesLibrary by creating
        val groovyListLibraryInMyLibrary by creating {
            sourceDirectory = groovyListLibrary.sourceDirectory
            target = "my-library"
        }
        val groovyUtilitiesLibraryInMyLibrary by creating {
            sourceDirectory = groovyUtilitiesLibrary.sourceDirectory
            target = "my-library"
        }

        val projectInfoPlugin by creating

        val precompiledScriptPluginUtils by creating {
            target = "convention-plugins"
        }
        val precompiledScriptPluginUtilsInBuildSrc by creating {
            sourceDirectory = precompiledScriptPluginUtils.sourceDirectory
            target = "buildSrc"
        }
        val problemsApiUsage by creating
    }

    // TODO: Do this lazily so we don't need to walk the filesystem during configuration
    // iterate through each snippets and record their names and locations
    val directoriesOnly = FileFilter { it.isDirectory }
    val topLevelDirs = file("src/snippets").listFiles(directoriesOnly).orEmpty()
    val snippetDirs = topLevelDirs.flatMap { it.listFiles(directoriesOnly).orEmpty().toList() }
        .filter { dir ->
            File(dir, "kotlin").exists() || File(dir, "groovy").exists()
        }

    snippetDirs.forEach { snippetDir ->
        val snippetName = snippetDir.name
        val categoryName = snippetDir.parentFile.name
        val id = org.gradle.docs.internal.StringUtils.toLowerCamelCase("snippet-$categoryName-$snippetName")
        publishedSamples.create(id) {
            description = "Snippet from $snippetDir"
            category = "Other"
            readmeFile = file("src/snippets/default-readme.adoc")
            sampleDirectory = snippetDir
            promoted = false
        }
    }

    publishedSamples {
        val buildingAndroidApps by creating {
            sampleDirectory = samplesRoot.dir("android-application")
            description = "Build a simple Android app."
            category = "Android"
            common {
                from(templates.named("javaAndroidApplication"))
            }
        }
        val buildingSpringBootWebApplications by creating {
            sampleDirectory = samplesRoot.dir("spring-boot-web-application")
            description = "Build a simple Spring Boot application."
            category = "Spring"
            common {
                from(templates.named("springBootWebApplication"))
            }
        }
        val incubatingJvmMultiProjectWithAdditionalTestTypes by creating {
            sampleDirectory = samplesRoot.dir("incubating/java/jvm-multi-project-with-additional-test-types")
            displayName = "Using additional test types with Test Suites (Incubating)"
            description = "Add an additional test type (e.g. integration tests) to a project using the new Test Suites API."
            category = "Java"

            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5IntegrationTestForApplication"))

                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaJunit5TestForListLibrary"))
                from(templates.named("javaJunit5IntegrationTestForUtilitiesLibrary"))

                from(templates.named("javaUtilitiesLibraryAsSubproject"))
            }
        }
        val incubatingJavaModulesMultiProjectWithIntegrationTests by creating {
            sampleDirectory = samplesRoot.dir("incubating/java/modules-multi-project-with-integration-tests")
            displayName = "Building Java Modules with Blackbox Tests with Test Suites (Incubating)"
            description = "Build Java Modules with blackbox integration tests using the new Test Suites API."
            category = "Java Modules"
            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaUtilitiesLibraryAsSubproject"))
                from(templates.named("javaModuleInfoForListLibrary"))
                from(templates.named("javaModuleInfoForUtilitiesLibrary"))
                from(templates.named("javaModuleInfoForApplication"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5TestForListLibrary"))

                from(templates.named("javaJunit5IntegrationTestForApplication"))
                from(templates.named("javaJunit5ModuleInfoForApplication"))
                from(templates.named("javaJunit5IntegrationTestForUtilitiesLibrary"))
                from(templates.named("javaJunit5ModuleInfoForUtilitiesLibrary"))
            }
        }
        val incubatingPublishingConventionPlugins by creating {
            sampleDirectory = samplesRoot.dir("incubating/build-organization/publishing-convention-plugins")
            displayName = "Sharing build logic in a multi-repo setup with Test Suites (Incubating)"
            description = "Organize and publish build logic for reuse in other projects using the new Test Suites API."
            category = "Java"

            common {
                from(templates.named("precompiledScriptPluginUtils"))
            }
        }
        val jvmMultiProjectWithAdditionalTestTypes by creating {
            sampleDirectory = samplesRoot.dir("java/jvm-multi-project-with-additional-test-types")
            displayName = "Using additional test types"
            description = "Add an additional test type (e.g. integration tests) to a project."
            category = "Java"

            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5IntegrationTestForApplication"))

                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaJunit5TestForListLibrary"))
                from(templates.named("javaJunit5IntegrationTestForUtilitiesLibrary"))

                from(templates.named("javaUtilitiesLibraryAsSubproject"))
            }
        }
        val jvmMultiProjectWithToolchains by creating {
            sampleDirectory = samplesRoot.dir("java/jvm-multi-project-with-toolchains")
            displayName = "Using toolchains"
            description = "Use toolchains to configure the JVM to use for compilation and testing."
            category = "Java"

            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5IntegrationTestForApplication"))

                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaJunit5TestForListLibrary"))
                from(templates.named("javaJunit5IntegrationTestForUtilitiesLibrary"))

                from(templates.named("javaUtilitiesLibraryAsSubproject"))
            }
        }
        val javaModulesMultiProject by creating {
            sampleDirectory = samplesRoot.dir("java/modules-multi-project")
            displayName = "Building Java Modules"
            description = "Build Java Modules and a modular Java application."
            category = "Java Modules"
            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaUtilitiesLibraryAsSubproject"))
                from(templates.named("javaModuleInfoForListLibrary"))
                from(templates.named("javaModuleInfoForUtilitiesLibrary"))
                from(templates.named("javaModuleInfoForApplication"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5TestForListLibrary"))
            }
        }
        val javaModulesMultiProjectWithIntegrationTests by creating {
            sampleDirectory = samplesRoot.dir("java/modules-multi-project-with-integration-tests")
            displayName = "Building Java Modules with Blackbox Tests"
            description = "Build Java Modules with blackbox integration tests."
            category = "Java Modules"
            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaUtilitiesLibraryAsSubproject"))
                from(templates.named("javaModuleInfoForListLibrary"))
                from(templates.named("javaModuleInfoForUtilitiesLibrary"))
                from(templates.named("javaModuleInfoForApplication"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5TestForListLibrary"))

                from(templates.named("javaJunit5IntegrationTestForApplication"))
                from(templates.named("javaJunit5ModuleInfoForApplication"))
                from(templates.named("javaJunit5IntegrationTestForUtilitiesLibrary"))
                from(templates.named("javaJunit5ModuleInfoForUtilitiesLibrary"))
            }
        }
        val javaModulesWithTransform by creating {
            sampleDirectory = samplesRoot.dir("java/modules-with-transform")
            displayName = "Building Java Modules with Legacy Libraries"
            description = "Build a modular Java application that integrates legacy libraries."
            category = "Java Modules"
            common {
                from(templates.named("buildSrcPluginJavaModuleTransform"))
            }
        }
        val jvmMultiProjectWithCodeCoverageDistribution by creating {
            sampleDirectory = samplesRoot.dir("incubating/java/jvm-multi-project-with-code-coverage-distribution")
            displayName = "Aggregating code coverage with JaCoCo from an application/distribution (Incubating)"
            description = "Report code coverage on the application/distribution of a multi-module project using link:https://www.jacoco.org/jacoco/[JaCoCo]."
            category = "Java"
            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaUtilitiesLibraryAsSubproject"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5TestForListLibrary"))
                from(templates.named("javaJunit5TestForUtilitiesLibrary"))
            }
        }
        val jvmMultiProjectWithCodeCoverageStandalone by creating {
            sampleDirectory = samplesRoot.dir("incubating/java/jvm-multi-project-with-code-coverage-standalone")
            displayName = "Aggregating code coverage with JaCoCo using a standalone utility project (Incubating)"
            description = "Report code coverage on a multi-module project using link:https://www.jacoco.org/jacoco/[JaCoCo]."
            category = "Java"
            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaUtilitiesLibraryAsSubproject"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5TestForListLibrary"))
                from(templates.named("javaJunit5TestForUtilitiesLibrary"))
            }
        }
        val jvmMultiProjectWithTestAggregationDistribution by creating {
            sampleDirectory = samplesRoot.dir("incubating/java/jvm-multi-project-with-test-aggregation-distribution")
            displayName = "Aggregating test results of an application/distribution (Incubating)"
            description = "Report all test results using the application/distribution of a multi-module project."
            category = "Java"
            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaUtilitiesLibraryAsSubproject"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5TestForListLibrary"))
                from(templates.named("javaJunit5TestForUtilitiesLibrary"))
            }
        }
        val jvmMultiProjectWithTestAggregationStandalone by creating {
            sampleDirectory = samplesRoot.dir("incubating/java/jvm-multi-project-with-test-aggregation-standalone")
            displayName = "Aggregating test results using a standalone utility project (Incubating)"
            description = "Report all test results using a standalone utility project as part of a multi-module project."
            category = "Java"
            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaListLibraryAsSubproject"))
                from(templates.named("javaUtilitiesLibraryAsSubproject"))
                from(templates.named("javaJunit5TestForApplication"))
                from(templates.named("javaJunit5TestForListLibrary"))
                from(templates.named("javaJunit5TestForUtilitiesLibrary"))
            }
        }
        val publishingJavaLibraries by creating {
            sampleDirectory = samplesRoot.dir("java/library-publishing")
            description = "Publish a Java library to a binary repository."
            category = "Java"
            common {
                from(templates.named("javaListLibraryInMyLibrary"))
                from(templates.named("javaUtilitiesLibraryInMyLibrary"))
            }
        }
        val publishingGroovyLibraries by creating {
            sampleDirectory = samplesRoot.dir("groovy/library-publishing")
            description = "Publish a Groovy library to a binary repository."
            category = "Groovy"
            common {
                from(templates.named("groovyListLibraryInMyLibrary"))
                from(templates.named("groovyUtilitiesLibraryInMyLibrary"))
            }
        }

        val gradlePlugin by creating {
            sampleDirectory = samplesRoot.dir("build-organization/gradle-plugin")
            description = "Organize your build logic into a Gradle plugin written in Java."
            category = "Build organization"
            common {
                from(templates.named("gradlePluginInJava"))
            }
        }

        val conventionPlugins by creating {
            sampleDirectory = samplesRoot.dir("build-organization/multi-project-with-convention-plugins")
            displayName = "Sharing build logic between subprojects"
            description = "Organize build logic into reusable pieces"
            category = "Build organization"
            common {
                from(templates.named("precompiledScriptPluginUtilsInBuildSrc"))
            }
        }

        val publishingConventionPlugins by creating {
            sampleDirectory = samplesRoot.dir("build-organization/publishing-convention-plugins")
            displayName = "Sharing build logic in a multi-repo setup"
            description = "Organize and publish build logic for reuse in other projects"
            category = "Build organization"
            common {
                from(templates.named("precompiledScriptPluginUtils"))
            }
        }

        val sharingConventionPluginsWithBuildLogic by creating {
            sampleDirectory = samplesRoot.dir("build-organization/sharing-convention-plugins-with-build-logic")
            displayName = "Sharing convention plugins with build logic build"
            description = "Reuse convention plugins in both main build and build logic build"
            category = "Build organization"
            common {
                from(templates.named("javaApplicationAsSubproject"))
                from(templates.named("javaUtilitiesLibraryAsSubproject"))
                from(templates.named("javaListLibraryAsSubproject"))
            }
        }
        val taskWithArguments by creating {
            sampleDirectory = samplesRoot.dir("writing-tasks/task-with-arguments")
            displayName = "Implementing Tasks with Command-line Arguments"
            description = "Pass arguments to a custom task."
            category = "Writing Custom Tasks"
        }
        val customTestTask by creating {
            sampleDirectory = samplesRoot.dir("writing-tasks/custom-test-task")
            displayName = "Implementing a task that runs tests"
            description = "Running tests outside of the JVM."
            category = "Writing Custom Tasks"
        }
        val tasksWithDependencyResolutionResultInputs by creating {
            sampleDirectory = samplesRoot.dir("writing-tasks/tasks-with-dependency-resolution-result-inputs")
            displayName = "Implementing tasks with dependency resolution result inputs"
            description = "Consume dependency resolution result inputs in tasks."
            category = "Writing Custom Tasks"
        }

        val publishingCredentials by creating {
            sampleDirectory = samplesRoot.dir("credentials-handling/publishing-credentials")
            description = "Publish to a password protected repository"
            category = "Using Credentials"
            common {
                from(templates.named("javaListLibrary"))
                from(templates.named("javaUtilitiesLibrary"))
            }
        }

        val credentialsForExternalToolViaStdin by creating {
            sampleDirectory = samplesRoot.dir("credentials-handling/pass-credentials-to-external-tool-via-stdin")
            displayName = "Supply credentials to external tool"
            description = "Pass credentials to an external tool via stdin using Gradle properties."
            category = "Using Credentials"
        }

        val structuringSoftwareProjects by creating {
            sampleDirectory = samplesRoot.dir("build-organization/structuring-software-projects")
            description = "Structuring a software product project with Gradle"
            category = "Build organization"
            common {
                from(templates.named("structuringSoftwareProjects"))
            }
        }

        val compositeBuildsBasics by creating {
            sampleDirectory = samplesRoot.dir("build-organization/composite-builds/basic")
            description = "Defining and using a composite build"
            category = "Build organization"
        }

        val compositeBuildsDeclaredSubstitutions by creating {
            sampleDirectory = samplesRoot.dir("build-organization/composite-builds/declared-substitution")
            description = "Applying and testing changes in downstream dependencies without publishing."
            category = "Build organization"
        }

        val compositeBuildsHierarchicalMultirepo by creating {
            sampleDirectory = samplesRoot.dir("build-organization/composite-builds/hierarchical-multirepo")
            description = "Defining and using a composite build to combine multiple independent builds."
            category = "Build organization"
        }

        val compositeBuildsPluginDevelopment by creating {
            sampleDirectory = samplesRoot.dir("build-organization/composite-builds/plugin-dev")
            description = "Developing a Gradle plugin in a build without publishing."
            category = "Build organization"
        }

        val crossProjectOutputSharing by creating {
            sampleDirectory = samplesRoot.dir("build-organization/cross-project-output-sharing")
            displayName = "Sharing task outputs across projects in a multi-project build"
            description = "Sharing a file made by a task in one Gradle project, with a task in another Gradle project."
            category = "Build organization"
        }

        val problemsApiUsage by creating {
            sampleDirectory = samplesRoot.dir("ide/problems-api-usage")
            displayName = "Reporting and receiving problems via the Problems API"
            description = "Reporting problems from plugins and consuming it in IDE integrations"
            category = "IDE integration"
            common {
                from(templates.named("problemsApiUsage"))
            }
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
        // Only execute C++ sample tests on Linux because it is the configured target
        if (!OperatingSystem.current().isLinux) {
            excludeTestsMatching("org.gradle.docs.samples.*.building-cpp-*")
        }
        // Only execute Swift sample tests on OS X because it is the configured target
        if (!OperatingSystem.current().isMacOsX) {
            excludeTestsMatching("org.gradle.docs.samples.*.building-swift-*")
        }
        // Only execute Groovy sample tests on Java < 9 to avoid warnings in output
        if (javaVersion.isJava9Compatible) {
            excludeTestsMatching("org.gradle.docs.samples.*.building-groovy-*")
        }

        if (OperatingSystem.current().isWindows && javaVersion.isCompatibleWith(JavaVersion.VERSION_18)) {
            // Disable tests that suffer from charset issues under JDK 18 for now
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-custom-model-internal-views_*_softwareModelExtend-iv-model")
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-model-rules-basic-rule-source-plugin_*_basicRuleSourcePlugin-model-task")
        }

        if (!javaVersion.isJava11Compatible) {
            // This test sets source and target compatibility to 11
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-kotlin-dsl-accessors_*")
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_12)) {
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-test-kit-gradle-version_*_testKitFunctionalTestSpockGradleDistribution")
        }

        if (!javaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
            // Android requires Java 17+
            excludeTestsMatching("org.gradle.docs.samples.*.building-android-*")
            excludeTestsMatching("org.gradle.docs.samples.*.structuring-software-projects*android-app")
            // Umbrella build project also contains Android projects
            excludeTestsMatching("org.gradle.docs.samples.*.structuring-software-projects_*_umbrella-build")
            // AGP AND KMP are tested on Java 17 only
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-dependency-management-declaring-configurations-*")
            // Spring Boot requires Java 17+
            excludeTestsMatching("org.gradle.docs.samples.*.structuring-software-projects_*_build-server-application")
        }

        if (!javaVersion.isCompatibleWith(JavaVersion.VERSION_21)) {
            // Sample requests Java 21
            excludeTestsMatching("org.gradle.docs.samples.*.custom-test-task_*_consumer")
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_22)) {
            // Incompatible for unknown reasons, investigation ongoing
            excludeTestsMatching("org.gradle.docs.samples.*.structuring-software-projects_*_build-android-app")
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_23)) {
            // SpotBugs doesn't support Java 23
            excludeTestsMatching("org.gradle.docs.samples.*.publishing-convention-plugins*")
            excludeTestsMatching("org.gradle.docs.samples.*.incubating-publishing-convention-plugins*")
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_25)) {
            // Kotlin does not yet support 25 JDK target
            excludeTestsMatching("org.gradle.docs.samples.*.building-kotlin-*")
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-best-practices-kotlin-std-lib*")
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-best-practices-use-convention-plugins-do_kotlin*")
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_26)) {
            // PMD doesn't support Java 26
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-code-quality-code-quality*")
        }

        if (OperatingSystem.current().isMacOsX && System.getProperty("os.arch") == "aarch64") {
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-native*")
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-swift*")
            excludeTestsMatching("org.gradle.docs.samples.*.building-swift*")
            // We don't have Android SDK installed on Mac M1 now
            excludeTestsMatching("org.gradle.docs.samples.*.building-android-*")
            excludeTestsMatching("org.gradle.docs.samples.*.structuring-software-projects*android-app")
        }
    }

    filter {
        // TODO(https://github.com/gradle/gradle/issues/22538)
        excludeTestsMatching("org.gradle.docs.samples.*.snippet-groovy-cross-compilation_*_crossCompilation")
        // disable while we're working on https://github.com/gradle/gradle-private/issues/4910
        excludeTestsMatching("*snippet-dependency-management-declaring-configurations-android_*")
        excludeTestsMatching("*snippet-dependency-management-declaring-configurations-kmp_*")
    }

    if (project.configurationCacheEnabledForDocsTests) {
        systemProperty("org.gradle.integtest.samples.cleanConfigurationCacheOutput", "true")
        systemProperty("org.gradle.integtest.executer", "configCache")

        filter {
            // Configuration cache samples enable configuration cache explicitly. We're not going to run them with the configuration cache executer.
            excludeTestsMatching("org.gradle.docs.samples.*.snippet-configuration-cache-*")
            excludeTestsMatching("*WithoutCC*")

            // Projects generated by gradle init enable configuration cache by default, no need to run them again.
            excludeTestsMatching("org.gradle.docs.samples.*building-*")

            // These tests cover features that are not planned to be supported in the first stable release of the configuration cache.
            val testsForUnsupportedFeatures = listOf(
                "snippet-ant-add-behaviour-to-ant-target_groovy_addBehaviourToAntTarget",
                "snippet-ant-add-behaviour-to-ant-target_kotlin_addBehaviourToAntTarget",
                "snippet-ant-depends-on-ant-target_groovy_dependsOnAntTarget",
                "snippet-ant-depends-on-ant-target_kotlin_dependsOnAntTarget",
                "snippet-ant-depends-on-task_groovy_dependsOnTask",
                "snippet-ant-depends-on-task_kotlin_dependsOnTask",
                "snippet-ant-hello_groovy_antHello",
                "snippet-ant-hello_kotlin_antHello",
                "snippet-ant-rename-task_groovy_renameAntDelegate",
                "snippet-ant-rename-task_kotlin_renameAntDelegate",
                "snippet-ant-use-external-ant-task-with-config_groovy_useExternalAntTaskWithConfig",
                "snippet-ant-use-external-ant-task-with-config_kotlin_useExternalAntTaskWithConfig",
                "snippet-ant-ant-logging_groovy_antLogging",
                "snippet-ant-ant-logging_kotlin_antLogging",
                "snippet-buildlifecycle-task-execution-events_groovy_sanityCheck",
                "snippet-buildlifecycle-task-execution-events_groovy_taskExecutionEvents.groovy",
                "snippet-buildlifecycle-task-execution-events_kotlin_sanityCheck",
                "snippet-buildlifecycle-task-execution-events_kotlin_taskExecutionEvents.kotlin",
                "snippet-custom-model-internal-views_groovy_softwareModelExtend-iv-model",
                "snippet-custom-model-language-type_groovy_softwareModelExtend-components",

                // These snippets are not used in the documentation, but only in the integration tests.
                "snippet-dependency-management-working-with-dependencies-access-metadata-artifact_groovy_accessingMetadataArtifact",
                "snippet-dependency-management-working-with-dependencies-access-metadata-artifact_kotlin_accessingMetadataArtifact",
                "snippet-dependency-management-working-with-dependencies-iterate-artifacts_kotlin_iterating-artifacts",
                "snippet-dependency-management-working-with-dependencies-walk-graph_groovy_walking-dependency-graph",
                "snippet-dependency-management-working-with-dependencies-walk-graph_kotlin_walking-dependency-graph",

                "snippet-ide-eclipse_groovy_wtpWithXml",
                "snippet-ide-eclipse_kotlin_wtpWithXml",
                "snippet-ide-idea-additional-test-sources_groovy_ideaAdditionalTestSources",
                "snippet-ide-idea-additional-test-sources_kotlin_ideaAdditionalTestSources",
                "snippet-ide-idea_groovy_projectWithXml",
                "snippet-ide-idea_kotlin_projectWithXml",
                "snippet-init-scripts-custom-logger_groovy_customLogger.groovy",
                "snippet-init-scripts-custom-logger_kotlin_customLogger.kotlin",
                "snippet-model-rules-basic-rule-source-plugin_groovy_basicRuleSourcePlugin-all",
                "snippet-model-rules-basic-rule-source-plugin_groovy_basicRuleSourcePlugin-model-task",
                "snippet-model-rules-configure-as-required_groovy_modelDslConfigureRuleRunWhenRequired",
                "snippet-model-rules-configure-elements-of-map_groovy_modelDslModelMapNestedAll",
                "snippet-model-rules-initialization-rule-runs-before-configuration-rules_groovy_modelDslInitializationRuleRunsBeforeConfigurationRule",
                "snippet-native-binaries-cpp_groovy_nativeComponentReport",
                "snippet-native-binaries-cunit_groovy_assembleDependentComponents",
                "snippet-native-binaries-cunit_groovy_assembleDependentComponentsReport",
                "snippet-native-binaries-cunit_groovy_buildDependentComponents",
                "snippet-native-binaries-cunit_groovy_buildDependentComponentsReport",
                "snippet-native-binaries-cunit_groovy_completeCUnitExample",
                "snippet-native-binaries-cunit_groovy_dependentComponentsReport",
                "snippet-native-binaries-cunit_groovy_dependentComponentsReportAll",
            )

            // These tests use third-party plugins at versions that may not support the configuration cache properly.
            // The tests should be removed from this list when the plugin is updated to the version that works with the configuration cache properly.
            val testsWithThirdPartyFailures = listOf(
                "structuring-software-projects_groovy_aggregate-reports",
                "structuring-software-projects_groovy_build-android-app",
                "structuring-software-projects_groovy_build-server-application",
                "structuring-software-projects_groovy_umbrella-build",
                "structuring-software-projects_kotlin_aggregate-reports",
                "structuring-software-projects_kotlin_build-android-app",
                "structuring-software-projects_kotlin_build-server-application",
                "structuring-software-projects_kotlin_umbrella-build",
            )

            // These tests cover features that the configuration cache doesn't support yet, but we plan to do that before hitting stable.
            // The tests should be removed from this list when the feature becomes supported.
            val testsForNotYetSupportedFeatures = listOf(
                // TODO(https://github.com/gradle/gradle/issues/14880)
                "snippet-dependency-management-working-with-dependencies-iterate-dependencies_groovy_iterating-dependencies",
                "snippet-dependency-management-working-with-dependencies-iterate-dependencies_kotlin_iterating-dependencies",

                // TODO(https://github.com/gradle/gradle/issues/22879) The snippet extracts build logic into a method and calls the method at execution time
                "snippet-tutorial-ant-loadfile-with-method_groovy_antLoadfileWithMethod",
                "snippet-tutorial-ant-loadfile-with-method_kotlin_antLoadfileWithMethod",
            )

            // Tests that can and has to be fixed to run with the configuration cache enabled.
            // Set the Gradle property runBrokenConfigurationCacheDocsTests=true to run tests from this list or any of the lists above.
            val testsToBeFixedForConfigurationCache = listOf(
                "snippet-build-cache-configure-task_groovy_configureTask",
                "snippet-build-cache-configure-task_kotlin_configureTask",
                // TODO(mlopatkin) These snippets use bintray plugin which is not fully CC-compatible. Remove bintray plugin from samples.
                "snippet-plugins-buildscript_groovy_sanityCheck",
                "snippet-plugins-buildscript_kotlin_sanityCheck",
                "snippet-plugins-dsl_groovy_sanityCheck",
                "snippet-plugins-dsl_kotlin_sanityCheck",
                // TODO(lkasso) remove this when config cache is working later but needed to merge for now.
                "snippet-dependency-management-introduction-core-dependencies_groovy_sanityCheck",
                "snippet-dependency-management-introduction-core-dependencies_kotlin_sanityCheck",
                "snippet-dependency-management-introduction-core-dependencies_groovy_dependencyIntroReport",
                "snippet-dependency-management-introduction-core-dependencies_kotlin_dependencyIntroReport",
                "snippet-dependency-management-catalogs-toml-simple_groovy_sanityCheck",
                "snippet-dependency-management-catalogs-toml-simple_kotlin_sanityCheck",
                "snippet-dependency-management-catalogs-toml-simple_groovy_resolve",
                "snippet-dependency-management-catalogs-toml-simple_kotlin_resolve",
                "snippet-dependency-management-catalogs-platforms_groovy_sanityCheck",
                "snippet-dependency-management-catalogs-platforms_kotlin_sanityCheck",
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
            // samples generated by init tasks explicitly enable configuration cache,
            // so we don't need to run them again
            excludeTestsMatching("*building-*-applications_groovy_build*")
            excludeTestsMatching("*building-*-applications_kotlin_build*")
            excludeTestsMatching("*building-*-libraries_groovy_build*")
            excludeTestsMatching("*building-*-libraries_kotlin_build*")
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
