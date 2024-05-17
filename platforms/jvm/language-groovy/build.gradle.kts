plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Adds support for building Groovy projects"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":build-option"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":worker-processes"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":jvm-services"))
    implementation(project(":workers"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":files"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyDoc)
    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.inject)

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":internal-testing"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":core")))

    testFixturesApi(testFixtures(project(":language-jvm")))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(testFixtures(project(":model-core")))
    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.javaParser) {
        because("The Groovy docs inspects the dependencies at compile time")
    }
    integTestImplementation(libs.nativePlatform) {
        because("Required for SystemInfo")
    }

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/compile/**")
    excludePatterns.add("org/gradle/api/tasks/javadoc/**")
}
