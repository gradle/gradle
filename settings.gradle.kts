import gradlebuild.module
import gradlebuild.packaging
import gradlebuild.platform
import gradlebuild.testing
import gradlebuild.unassigned
import org.gradle.api.internal.FeaturePreviews

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    includeBuild("build-logic-settings")
}

plugins {
    id("gradlebuild.build-environment")
    id("gradlebuild.configuration-cache-compatibility")
    id("gradlebuild.version-catalogs")
    id("gradlebuild.default-settings-plugins")
    id("gradlebuild.architecture-docs")
}

includeBuild("build-logic-commons")
includeBuild("build-logic")

apply(from = "gradle/shared-with-buildSrc/mirrors.settings.gradle.kts")

// If you include a new subproject here, consult internal documentation "Adding a new Build Tool subproject" page

// Gradle implementation projects
unassigned {
    subproject("core")
    subproject("build-events")
    subproject("composite-builds")
    subproject("core-api")
}

// Core platform
val core = platform("core") {

    // Core Runtime Module
    module("core-runtime") {
        subproject("base-asm")
        subproject("base-services")
        subproject("build-configuration")
        subproject("build-discovery")
        subproject("build-discovery-impl")
        subproject("build-discovery-reporting")
        subproject("build-operations")
        subproject("build-operations-trace")
        subproject("build-option")
        subproject("build-process-services")
        subproject("build-profile")
        subproject("build-state")
        subproject("classloaders")
        subproject("cli")
        subproject("client-services")
        subproject("collections")
        subproject("concurrent")
        subproject("daemon-main")
        subproject("daemon-protocol")
        subproject("daemon-services")
        subproject("daemon-server")
        subproject("daemon-logging")
        subproject("file-temp")
        subproject("files")
        subproject("functional")
        subproject("gradle-cli-main")
        subproject("gradle-cli")
        subproject("groovy-loader")
        subproject("installation-beacon")
        subproject("instrumentation-agent")
        subproject("instrumentation-agent-services")
        subproject("instrumentation-declarations")
        subproject("instrumentation-reporting")
        subproject("internal-instrumentation-api")
        subproject("internal-instrumentation-processor")
        subproject("io")
        subproject("stdlib-java-extensions")
        subproject("launcher")
        subproject("logging")
        subproject("logging-api")
        subproject("messaging")
        subproject("native")
        subproject("process-memory-services")
        subproject("process-services")
        subproject("report-rendering")
        subproject("serialization")
        subproject("service-lookup")
        subproject("service-provider")
        subproject("service-registry-builder")
        subproject("service-registry-impl")
        subproject("time")
        subproject("tooling-api-provider")
        subproject("versioned-cache")
        subproject("wrapper-main")
        subproject("wrapper-shared")
    }

    // Core Configuration Module
    module("core-configuration") {
        subproject("api-metadata")
        subproject("base-diagnostics")
        subproject("base-services-groovy")
        subproject("bean-serialization-services")
        subproject("configuration-cache")
        subproject("configuration-cache-base")
        subproject("configuration-problems-base")
        subproject("core-flow-services-api")
        subproject("core-kotlin-extensions")
        subproject("core-serialization-codecs")
        subproject("declarative-dsl-api")
        subproject("declarative-dsl-core")
        subproject("declarative-dsl-evaluator")
        subproject("declarative-dsl-provider")
        subproject("declarative-dsl-tooling-models")
        subproject("declarative-dsl-tooling-builders")
        subproject("dependency-management-serialization-codecs")
        subproject("encryption-services")
        subproject("file-collections")
        subproject("file-operations")
        subproject("flow-services")
        subproject("graph-isolation")
        subproject("graph-serialization")
        subproject("guava-serialization-codecs")
        subproject("input-tracking")
        subproject("isolated-action-services")
        subproject("java-api-extractor")
        subproject("kotlin-dsl")
        subproject("kotlin-dsl-provider-plugins")
        subproject("kotlin-dsl-tooling-builders")
        subproject("kotlin-dsl-tooling-models")
        subproject("kotlin-dsl-plugins")
        subproject("kotlin-dsl-integ-tests")
        subproject("stdlib-kotlin-extensions")
        subproject("stdlib-serialization-codecs")
        subproject("model-core")
        subproject("model-reflect")
        subproject("model-groovy")
        subproject("project-features")
        subproject("project-features-api")
        subproject("project-features-demos")
    }

    // Core Execution Module
    module("core-execution") {
        subproject("build-cache")
        subproject("build-cache-base")
        subproject("build-cache-example-client")
        subproject("build-cache-http")
        subproject("build-cache-local")
        subproject("build-cache-packaging")
        subproject("build-cache-spi")
        subproject("daemon-server-worker")
        subproject("execution")
        subproject("execution-e2e-tests")
        subproject("file-watching")
        subproject("hashing")
        subproject("persistent-cache")
        subproject("request-handler-worker")
        subproject("scoped-persistent-cache")
        subproject("snapshots")
        subproject("worker-main")
        subproject("workers")
    }
}

// Documentation Module
module("documentation") {
    subproject("docs")
    subproject("docs-asciidoctor-extensions-base")
    subproject("docs-asciidoctor-extensions")
    subproject("samples")
}

// IDE Module
module("ide") {
    subproject("base-ide-plugins")
    subproject("ide")
    subproject("ide-native")
    subproject("ide-plugins")
    subproject("problems")
    subproject("problems-api")
    subproject("problems-rendering")
    subproject("tooling-api")
    subproject("tooling-api-builders")
}

// Software Platform
val software = platform("software") {
    uses(core)
    subproject("base-compiler-worker")
    subproject("build-init")
    subproject("build-init-specs")
    subproject("build-init-specs-api")
    subproject("dependency-management")
    subproject("plugins-distribution")
    subproject("distributions-publishing")
    subproject("ivy")
    subproject("maven")
    subproject("platform-base")
    subproject("plugins-version-catalog")
    subproject("publish")
    subproject("resources")
    subproject("resources-http")
    subproject("resources-gcs")
    subproject("resources-s3")
    subproject("resources-sftp")
    subproject("reporting")
    subproject("security")
    subproject("signing")
    subproject("software-diagnostics")
    subproject("testing-base")
    subproject("testing-base-infrastructure")
    subproject("test-suites-base")
    subproject("version-control")
}

// JVM Platform
val jvm = platform("jvm") {
    uses(core)
    uses(software)
    subproject("antlr")
    subproject("ant-worker")
    subproject("code-quality")
    subproject("code-quality-workers")
    subproject("distributions-jvm")
    subproject("ear")
    subproject("groovy-compiler-worker")
    subproject("groovydoc-worker")
    subproject("jacoco")
    subproject("jacoco-workers")
    subproject("java-compiler-plugin")
    subproject("java-compiler-worker")
    subproject("java-platform")
    subproject("javadoc")
    subproject("jvm-compiler-worker")
    subproject("jvm-services")
    subproject("language-groovy")
    subproject("language-java")
    subproject("language-jvm")
    subproject("normalization-java")
    subproject("platform-jvm")
    subproject("plugins-application")
    subproject("plugins-groovy")
    subproject("plugins-java")
    subproject("plugins-java-base")
    subproject("plugins-java-library")
    subproject("plugins-jvm-test-fixtures")
    subproject("plugins-jvm-test-suite")
    subproject("plugins-test-report-aggregation")
    subproject("scala")
    subproject("scaladoc-worker")
    subproject("scala-compiler-worker")
    subproject("testing-jvm")
    subproject("testing-jvm-infrastructure")
    subproject("toolchains-jvm")
    subproject("toolchains-jvm-shared")
    subproject("war")
}

// Extensibility Platform
platform("extensibility") {
    uses(core)
    uses(jvm)
    subproject("plugin-use")
    subproject("plugin-development")
    subproject("unit-test-fixtures")
    subproject("test-kit")
}

// Native Platform
platform("native") {
    uses(core)
    uses(software)
    subproject("distributions-native")
    subproject("platform-native")
    subproject("language-native")
    subproject("tooling-native")
    subproject("testing-native")
}


// Develocity Module
module("enterprise") {
    subproject("enterprise")
    subproject("enterprise-logging")
    subproject("enterprise-operations")
    subproject("enterprise-plugin-performance")
    subproject("enterprise-workers")
}

packaging {
    subproject("distributions-dependencies") // platform for dependency versions
    subproject("core-platform")              // platform for Gradle distribution core
    subproject("distributions-full")
    subproject("public-api")                 // Public API publishing
    subproject("internal-build-reports")     // Internal utility and verification projects
}

testing {
    subproject("architecture-test")
    subproject("distributions-basics")
    subproject("distributions-core")
    subproject("distributions-integ-tests")
    subproject("integ-test")
    subproject("internal-architecture-testing")
    subproject("internal-integ-testing")
    subproject("internal-performance-testing")
    subproject("internal-testing")
    subproject("performance")
    subproject("precondition-tester")
    subproject("public-api-tests")
    subproject("soak")
    subproject("smoke-ide-test") // eventually should be owned by IDEX team
    subproject("smoke-test")
}

rootProject.name = "gradle"

FeaturePreviews.Feature.entries.forEach { feature ->
    if (feature.isActive) {
        enableFeaturePreview(feature.name)
    }
}

fun getBuildJavaHome() = System.getProperty("java.home")

gradle.settingsEvaluated {
    if ("true" == System.getProperty("org.gradle.ignoreBuildJavaVersionCheck")) {
        return@settingsEvaluated
    }

    if (JavaVersion.current() != JavaVersion.VERSION_17) {
        throw GradleException("This build requires JDK 17. It's currently ${getBuildJavaHome()}. You can ignore this check by passing '-Dorg.gradle.ignoreBuildJavaVersionCheck=true'.")
    }
}
