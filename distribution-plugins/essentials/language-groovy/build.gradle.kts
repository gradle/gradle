plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:worker-processes")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:file-temp")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:jvm-services")
    implementation("org.gradle:files")
    implementation(project(":workers"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))

    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyDoc)
    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.inject)

    testImplementation("org.gradle:base-services-groovy")
    testImplementation("org.gradle:resources")
    testImplementation("org.gradle:internal-testing")
    testImplementation(testFixtures("org.gradle:core"))

    testFixturesApi(testFixtures(project(":language-jvm")))
    testFixturesImplementation("org.gradle:core")
    testFixturesImplementation("org.gradle:base-services")

    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.javaParser) {
        because("The Groovy docs inspects the dependencies at compile time")
    }

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

classycle {
    excludePatterns.add("org/gradle/api/internal/tasks/compile/**")
    excludePatterns.add("org/gradle/api/tasks/javadoc/**")
}
