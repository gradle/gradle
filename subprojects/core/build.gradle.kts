plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public and internal 'core' Gradle APIs with implementation"

configurations {
    register("reports")
}

tasks.classpathManifest {
    optionalProjects.add("gradle-kotlin-dsl")
    // The gradle-runtime-api-info.jar is added by a 'distributions-...' project if it is on the (integration test) runtime classpath.
    // It contains information services in ':core' need to reason about the complete Gradle distribution.
    // To allow parts of ':core' code to be instantiated in unit tests without relying on this functionality, the dependency is optional.
    optionalProjects.add("gradle-runtime-api-info")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-services-groovy"))
    implementation(project(":build-operations"))
    implementation(project(":enterprise-operations"))
    implementation(project(":functional"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":resources"))
    implementation(project(":cli"))
    implementation(project(":build-option"))
    implementation(project(":native"))
    implementation(project(":model-core"))
    implementation(project(":persistent-cache"))
    implementation(project(":build-cache"))
    implementation(project(":build-cache-packaging"))
    implementation(project(":core-api"))
    implementation(project(":files"))
    implementation(project(":file-temp"))
    implementation(project(":file-collections"))
    implementation(project(":process-services"))
    implementation(project(":jvm-services"))
    implementation(project(":model-groovy"))
    implementation(project(":snapshots"))
    implementation(project(":file-watching"))
    implementation(project(":execution"))
    implementation(project(":worker-processes"))
    implementation(project(":normalization-java"))
    implementation(project(":wrapper-shared"))
    implementation(project(":internal-instrumentation-api"))

    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyAstbuilder)
    implementation(libs.groovyConsole)
    implementation(libs.groovyDateUtil)
    implementation(libs.groovyDatetime)
    implementation(libs.groovyDoc)
    implementation(libs.groovyJson)
    implementation(libs.groovyNio)
    implementation(libs.groovySql)
    implementation(libs.groovyTemplates)
    implementation(libs.groovyTest)
    implementation(libs.groovyXml)
    implementation(libs.ant)
    implementation(libs.fastutil)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.asmTree)
    implementation(libs.asmCommons)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.commonsCompress)
    implementation(libs.nativePlatform)
    implementation(libs.xmlApis)
    implementation(libs.tomlj)
    implementation(libs.javaParser) {
        because("The Groovy compiler inspects the dependencies at compile time")
    }

    compileOnly(libs.futureKotlin("stdlib")) {
        because("it needs to forward calls from instrumented code to the Kotlin standard library")
    }

    testImplementation(project(":platform-jvm"))
    testImplementation(project(":testing-base"))
    testImplementation(project(":platform-native"))
    testImplementation(libs.jsoup)
    testImplementation(libs.log4jToSlf4j)
    testImplementation(libs.jclToSlf4j)

    testFixturesApi(project(":base-services")) {
        because("test fixtures expose Action")
    }
    testFixturesApi(project(":base-services-groovy")) {
        because("test fixtures expose AndSpec")
    }
    testFixturesApi(project(":core-api")) {
        because("test fixtures expose Task")
    }
    testFixturesApi(project(":logging")) {
        because("test fixtures expose Logger")
    }
    testFixturesApi(project(":model-core")) {
        because("test fixtures expose IConventionAware")
    }
    testFixturesApi(project(":build-cache")) {
        because("test fixtures expose BuildCacheController")
    }
    testFixturesApi(project(":execution")) {
        because("test fixtures expose OutputChangeListener")
    }
    testFixturesApi(project(":native")) {
        because("test fixtures expose FileSystem")
    }
    testFixturesApi(project(":file-collections")) {
        because("test fixtures expose file collection types")
    }
    testFixturesApi(project(":file-temp")) {
        because("test fixtures expose temp file types")
    }
    testFixturesApi(project(":resources")) {
        because("test fixtures expose file resource types")
    }
    testFixturesApi(testFixtures(project(":persistent-cache"))) {
        because("test fixtures expose cross-build cache factory")
    }
    testFixturesApi(project(":process-services")) {
        because("test fixtures expose exec handler types")
    }
    testFixturesApi(testFixtures(project(":hashing"))) {
        because("test fixtures expose test hash codes")
    }
    testFixturesApi(testFixtures(project(":snapshots"))) {
        because("test fixtures expose file snapshot related functionality")
    }
    testFixturesImplementation(project(":build-option"))
    testFixturesImplementation(project(":messaging"))
    testFixturesImplementation(project(":persistent-cache"))
    testFixturesImplementation(project(":snapshots"))
    testFixturesImplementation(project(":normalization-java"))
    testFixturesImplementation(project(":enterprise-operations"))
    testFixturesImplementation(libs.ivy)
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.ant)
    testFixturesImplementation(libs.groovyAnt)
    testFixturesImplementation(libs.asm)
    testFixturesImplementation(project(":dependency-management")) {
        because("Used in VersionCatalogErrorMessages for org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder.getExcludedNames")
    }

    testFixturesRuntimeOnly(project(":plugin-use")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(project(":workers")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    testFixturesRuntimeOnly(project(":composite-builds")) {
        because("We always need a BuildStateRegistry service implementation")
    }

    testImplementation(project(":dependency-management"))

    testImplementation(testFixtures(project(":core-api")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":snapshots")))
    testImplementation(testFixtures(project(":execution")))

    integTestImplementation(project(":workers"))
    integTestImplementation(project(":dependency-management"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":plugins"))
    integTestImplementation(project(":war"))
    integTestImplementation(libs.jansi)
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.littleproxy)
    integTestImplementation(testFixtures(project(":native")))
    integTestImplementation(testFixtures(project(":file-temp")))


    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Some tests utilise the 'java-gradle-plugin' and with that TestKit, some also use the 'war' plugin")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))

    annotationProcessor(project(":internal-instrumentation-processor"))
    annotationProcessor(platform(project(":distributions-dependencies")))

    testAnnotationProcessor(project(":internal-instrumentation-processor"))
    testAnnotationProcessor(platform(project(":distributions-dependencies")))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreAnnotationProcessing() // Without this, javac will complain about unclaimed annotations
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

tasks.test {
    setForkEvery(200)
}

// Disable annotation processing for Groovy, as it is not compatible with Groovy IC
tasks.withType<GroovyCompile>().configureEach {
    options.compilerArgs.add("-proc:none")
}

tasks.compileTestGroovy {
    groovyOptions.fork("memoryInitialSize" to "128M", "memoryMaximumSize" to "1G")
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
