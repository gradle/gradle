plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins for integration with native projects in XCode and Visual Studio IDEs"

sourceSets {
    main {
        // Incremental Groovy joint-compilation doesn't work with the Error Prone annotation processor
        errorprone.enabled = false
    }
}

dependencies {
    api(libs.groovy)
    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.plist)
    api(project(":base-ide-plugins"))
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":file-collections"))
    api(project(":ide"))
    api(project(":java-language-extensions"))
    api(project(":language-native"))
    api(project(":platform-base"))
    api(project(":platform-native"))
    api(project(":service-provider"))

    implementation(project(":model-core"))
    implementation(project(":testing-native"))
    implementation(project(":logging-api"))
    implementation(libs.commonsLang)

    runtimeOnly(project(":dependency-management"))
    runtimeOnly(project(":testing-base"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":language-native")))
    testImplementation(testFixtures(project(":version-control")))

    integTestImplementation(project(":native"))
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.jgit)

    testFixturesApi(testFixtures(project(":ide")))
    testFixturesImplementation(libs.plist)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.groovyXml)
    testFixturesImplementation(testFixtures(project(":ide")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native"))
}

integTest.usesJavadocCodeSnippets = true
