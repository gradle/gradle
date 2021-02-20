plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.update-init-template-versions")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":dependency-management"))
    implementation(project(":platform-base"))
    implementation(project(":platform-native"))
    implementation(project(":plugins"))
    implementation(project(":wrapper"))

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    implementation("org.apache.maven:maven-plugin-api:3.6.3")
    implementation("org.apache.maven:maven-compat:3.6.3")
    runtimeOnly("org.apache.maven.wagon:wagon-http:3.4.2")
    runtimeOnly("org.apache.maven.wagon:wagon-provider-api:3.4.2")
    runtimeOnly("org.eclipse.aether:aether-connector-basic:1.1.0")
    runtimeOnly("org.eclipse.aether:aether-transport-wagon:1.1.0")

    testImplementation(project(":cli"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":process-services"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-native")))

    testFixturesImplementation(project(":base-services"))

    integTestImplementation(project(":native"))
    integTestImplementation(libs.jetty)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
