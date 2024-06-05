plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
    id("gradlebuild.kotlin-experimental-contracts")
}

description = "Configuration cache implementation"

val configurationCacheReportPath by configurations.creating {
    isVisible = false
    isCanBeConsumed = false
    attributes { attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("configuration-cache-report")) }
}

// You can have a faster feedback loop by running `configuration-cache-report` as an included build
// See https://github.com/gradle/configuration-cache-report#development-with-gradlegradle-and-composite-build
dependencies {
    configurationCacheReportPath(libs.configurationCacheReport)
}

tasks.processResources {
    from(zipTree(configurationCacheReportPath.elements.map { it.first().asFile })) {
        into("org/gradle/configurationcache/problems")
        exclude("META-INF/**")
    }
}

// The integration tests in this project do not need to run in 'config cache' mode.
tasks.configCacheIntegTest {
    enabled = false
}

// For now, goal is to see this transform executed, which will fail with a Runtime Exception
@CacheableTransform
abstract class StripBindingTransform : TransformAction<StripBindingTransform.StripBindingParameters> {
    interface StripBindingParameters : TransformParameters

    @InputArtifact
    @Classpath
    abstract fun getInputArtifact(): Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = getInputArtifact().get().asFile
        val output = outputs.file(input.name + ".txt")
        output.writeText("File size: " + input.length())
        throw RuntimeException("Transform running: Should strip binding from jar now...")
    }
}

configurations {
    // Request variants for compilation using the "no-binding" variant identified by the SLF4J_ATTRIBUTE
    // The only dependency that has uses this attribute is slf4j
    compileClasspath {
        attributes {
            attribute(gradlebuild.SLF4J_ATTRIBUTE, "no-binding")
        }
    }
}

dependencies {
    attributesSchema {
        attribute(gradlebuild.SLF4J_ATTRIBUTE)
    }

    // All variants of SLF4J get the SLF4J_ATTRIBUTE attribute with value "has-binding"
    components {
        withModule<gradlebuild.HasBindingAttributeRule>(libs.slf4jApi)
    }

    artifactTypes.getByName("jar") {
        // For artifacts for dependencies that contain the SLF4J_ATTRIBUTE (should be just slf4j from the ComponentMetadataRule above),
        // we want to request the "no-binding" variant
        if (attributes.contains(gradlebuild.SLF4J_ATTRIBUTE)) {
            attributes.attribute(gradlebuild.SLF4J_ATTRIBUTE, "no-binding")
        }
    }

    // Register a transform from "has-binding" to "no-binding"
    registerTransform(StripBindingTransform::class) {
        from.attribute(gradlebuild.SLF4J_ATTRIBUTE, "has-binding")
        to.attribute(gradlebuild.SLF4J_ATTRIBUTE, "no-binding")
    }

    // The slf4j dependency declaration moved here for clarity
    implementation(libs.slf4jApi)

    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(projects.configurationProblemsBase)
    api(project(":base-services"))
    api(project(":build-operations"))
    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    api(project(":build-option"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":enterprise-operations"))
    api(project(":file-collections"))
    api(project(":file-temp"))
    api(project(":functional"))
    api(projects.graphSerialization)
    api(project(":hashing"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":model-core"))
    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    api(project(":native"))
    api(project(":persistent-cache"))
    api(project(":plugin-use"))
    api(project(":resources"))
    api(project(":snapshots"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.kotlinStdlib)

    // TODO - it might be good to allow projects to contribute state to save and restore, rather than have this project know about everything
    implementation(project(":base-services-groovy"))
    implementation(project(":build-events"))
    implementation(projects.coreKotlinExtensions)
    implementation(project(":execution"))
    implementation(project(":files"))
    implementation(project(":file-watching"))
    implementation(projects.flowServices)
    implementation(projects.guavaSerializationCodecs)
    implementation(project(":input-tracking"))
    implementation(project(":platform-jvm"))
    implementation(projects.problemsApi)
    implementation(project(":process-services"))
    implementation(project(":publish"))
    implementation(projects.serialization)
    implementation(projects.stdlibKotlinExtensions)
    implementation(projects.stdlibSerializationCodecs)
    implementation(project(":tooling-api"))

    implementation(libs.asm)
    implementation(libs.fastutil)
    implementation(libs.groovyJson)
    implementation(libs.guava)

    runtimeOnly(project(":composite-builds"))
    runtimeOnly(project(":resources-http"))
    // TODO - move the isolatable serializer to model-core to live with the isolatable infrastructure
    runtimeOnly(project(":workers"))

    runtimeOnly(libs.kotlinReflect)

    testImplementation(projects.io)
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.mockitoKotlin2)
    testImplementation(libs.kotlinCoroutinesDebug)

    integTestImplementation(project(":jvm-services"))
    integTestImplementation(project(":tooling-api"))
    integTestImplementation(project(":platform-jvm"))
    integTestImplementation(project(":test-kit"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":cli"))
    integTestImplementation(project(":workers"))

    integTestImplementation(libs.guava)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.inject)
    integTestImplementation("com.microsoft.playwright:playwright:1.20.1")

    integTestImplementation(testFixtures(project(":tooling-api")))
    integTestImplementation(testFixtures(project(":dependency-management")))
    integTestImplementation(testFixtures(project(":jacoco")))
    integTestImplementation(testFixtures(project(":model-core")))

    crossVersionTestImplementation(project(":cli"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("Includes tests for builds with the enterprise plugin and TestKit involved; ConfigurationCacheJacocoIntegrationTest requires JVM distribution")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/configurationcache/**")
}
