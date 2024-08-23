plugins {
    // Uninstrumented since it is a mix of Groovy and Java code,
    // and additionally we don't plan to have upgrades for IDE plugins.
    id("gradlebuild.distribution.uninstrumented.api-java")
}

description = "Plugins for integration with native projects in XCode and Visual Studio IDEs"

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}

dependencies {
    compileOnly(libs.jetbrainsAnnotations)

    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.plist)
    api(projects.baseIdePlugins)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.fileCollections)
    api(projects.ide)
    api(projects.stdlibJavaExtensions)
    api(projects.languageNative)
    api(projects.platformBase)
    api(projects.platformNative)
    api(projects.serviceProvider)

    implementation(projects.modelCore)
    implementation(projects.testingNative)
    implementation(projects.loggingApi)
    implementation(projects.serviceLookup)
    implementation(libs.commonsLang)

    runtimeOnly(projects.dependencyManagement)
    runtimeOnly(projects.testingBase)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.platformNative))
    testImplementation(testFixtures(projects.languageNative))
    testImplementation(testFixtures(projects.versionControl))

    integTestImplementation(projects.native)
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.jgit)

    testFixturesApi(testFixtures(projects.ide))
    testFixturesImplementation(libs.plist)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.groovyXml)
    testFixturesImplementation(testFixtures(projects.ide))

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsNative)
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
