import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.Companion.shadowRuntimeElements
import gradlebuild.basics.PublicKotlinDslApi
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("gradlebuild.distribution.api-kotlin")
    id("gradlebuild.kotlin-dsl-dependencies-embedded")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
    id("com.gradleup.shadow").version("9.0.0-beta11")
}

description = "Kotlin DSL Provider"

dependencies {
    api(projects.buildProcessServices)
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.core)
    api(projects.coreApi)
    api(projects.concurrent)
    api(projects.fileOperations)
    api(projects.hashing)
    api(projects.kotlinDslToolingModels)
    api(projects.loggingApi)
    api(projects.persistentCache)
    api(projects.stdlibJavaExtensions)
    api(projects.toolingApi)

    api(libs.groovy)
    api(libs.guava)
    api(libs.kotlinStdlib)
    api(libs.inject)
    api(libs.slf4jApi)

    implementation(projects.baseAsm)
    implementation(projects.instrumentationReporting)
    implementation(projects.buildOperations)
    implementation(projects.buildOption)
    implementation(projects.coreKotlinExtensions)
    implementation(projects.declarativeDslEvaluator)
    implementation(projects.declarativeDslInternalUtils)
    implementation(projects.declarativeDslProvider)
    implementation(projects.enterpriseLogging)
    implementation(projects.enterpriseOperations)
    implementation(projects.execution)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.files)
    implementation(projects.functional)
    implementation(projects.io)
    implementation(projects.logging)
    implementation(projects.messaging)
    implementation(projects.modelCore)
    implementation(projects.resources)
    implementation(projects.scopedPersistentCache)
    implementation(projects.serialization)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.snapshots)
    implementation(projects.softwareFeatures)

    implementation(projects.javaApiExtractor)
    implementation("org.gradle:kotlin-dsl-shared-runtime")

    implementation(libs.asm)
    implementation(libs.jspecify)
    implementation(libs.kotlinReflect)

    implementation(libs.kotlinCompilerEmbeddable)
    api(libs.futureKotlin("script-runtime"))

    api(libs.futureKotlin("scripting-common")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-jvm")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-jvm-host")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("scripting-compiler-embeddable")) {
        isTransitive = false
    }
    api(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("sam-with-receiver-compiler-plugin")) {
        isTransitive = false
    }
    implementation(libs.futureKotlin("assignment-compiler-plugin-embeddable")) {
        isTransitive = false
    }
    shadow(libs.futureKotlin("metadata-jvm")) {
        isTransitive = false
    }

    runtimeOnly(libs.kotlinBuildToolsImpl) {
        isTransitive = false
    }

    testImplementation(projects.buildCacheHttp)
    testImplementation(projects.buildCacheLocal)
    testImplementation(projects.buildInit)
    testImplementation(projects.jacoco)
    testImplementation(projects.platformNative) {
        because("BuildType from platform-native is used in ProjectAccessorsClassPathTest")
    }
    testImplementation(projects.platformJvm)
    testImplementation(projects.versionControl)
    testImplementation(testFixtures(projects.core))
    testImplementation(libs.ant)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.jacksonKotlin)
    testImplementation(libs.archunit)
    testImplementation(libs.kotlinCoroutines)
    testImplementation(libs.awaitility)

    integTestImplementation(projects.buildOption) {
        because("KotlinSettingsScriptIntegrationTest makes uses of FeatureFlag")
    }
    integTestImplementation(projects.languageGroovy) {
        because("ClassBytesRepositoryTest makes use of Groovydoc task.")
    }
    integTestImplementation(projects.internalTesting)
    integTestImplementation(libs.mockitoKotlin)

    testRuntimeOnly(projects.distributionsNative) {
        because("SimplifiedKotlinScriptEvaluator reads default imports from the distribution (default-imports.txt) and BuildType from platform-native is used in ProjectAccessorsClassPathTest.")
    }

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.fileTemp)
    testFixturesImplementation(projects.resources)
    testFixturesImplementation(projects.kotlinDslToolingBuilders)
    testFixturesImplementation(projects.testKit)
    testFixturesImplementation(projects.internalTesting)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.unitTestFixtures)
    testFixturesImplementation(projects.serviceRegistryImpl)

    testFixturesImplementation(testFixtures(projects.hashing))

    testFixturesImplementation(libs.kotlinCompilerEmbeddable)

    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.mockitoKotlin)
    testFixturesImplementation(libs.jacksonKotlin)
    testFixturesImplementation(libs.asm)

    integTestDistributionRuntimeOnly(projects.distributionsBasics)
}

// Relocate kotlin-metadata-jvm
configurations.compileOnly {
    extendsFrom(configurations.shadow.get())
}
configurations.testImplementation {
    extendsFrom(configurations.shadow.get())
}
tasks.shadowJar {
    archiveClassifier = ""
    configurations = setOf(project.configurations.shadow.get())
    relocate("kotlin.metadata", "org.gradle.kotlin.dsl.internal.relocated.kotlin.metadata")
    relocate("kotlinx.metadata", "org.gradle.kotlin.dsl.internal.relocated.kotlinx.metadata")
    mergeServiceFiles()
    exclude("META-INF/kotlin-metadata-jvm.kotlin_module")
    exclude("META-INF/kotlin-metadata.kotlin_module")
    exclude("META-INF/metadata.jvm.kotlin_module")
    exclude("META-INF/metadata.kotlin_module")
}
val beforeShadowClassifier = "before-shadow"
tasks.jar {
    archiveClassifier = beforeShadowClassifier
}
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
// Replace the standard jar with the one built by 'shadowJar' in both api and runtime variants
configurations.apiElements {
    outgoing.artifacts.removeIf { it.classifier == beforeShadowClassifier && it.extension == "jar" }
    outgoing.artifact(tasks.shadowJar) {
        builtBy(tasks.shadowJar)
    }
}
configurations.runtimeElements {
    outgoing.artifacts.removeIf { it.classifier == beforeShadowClassifier && it.extension == "jar" }
    outgoing.artifact(tasks.shadowJar) {
        builtBy(tasks.shadowJar)
    }
}
// Restore Kotlin's friendPath so tests and fixtures can access internals
tasks.compileTestKotlin {
    friendPaths.from(tasks.shadowJar)
}
tasks.compileTestFixturesKotlin {
    friendPaths.from(tasks.shadowJar)
}
// Remove spurious configuration from shadow plugin to resolve ambiguity building the distribution
// It seems to win over runtimeElements where it should not
configurations.remove(configurations.shadowRuntimeElements.get())

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/**")
}

testFilesCleanup.reportOnly = true

strictCompile {
    ignoreDeprecations()
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}

// Filter out what goes into the public API
configure<KotlinJvmProjectExtension> {
    val filterKotlinDslApi = tasks.register<Copy>("filterKotlinDslApi") {
        dependsOn(target.compilations.named("main").flatMap { it.compileTaskProvider })
        into(layout.buildDirectory.dir("generated/kotlin-abi-filtered"))
        from(layout.buildDirectory.dir("generated/kotlin-abi")) {
            includeEmptyDirs = false
            include(PublicKotlinDslApi.includes)
            // Those leak in the public API - see org.gradle.kotlin.dsl.NamedDomainObjectContainerScope for example
            include("org/gradle/kotlin/dsl/support/delegates/*")
            include("META-INF/*.kotlin_module")
            // We do not exclude inlined functions, they are needed for compilation
        }
    }

    configurations.apiStubElements.configure {
        outgoing.artifacts.clear()
        outgoing.artifact(filterKotlinDslApi)
    }
}
