
// When flag is set, use an Kotlin EAP to allow Gradle to be built when instant execution is enabled
// Remove this when upgrading to Kotlin 1.3.70
val useKotlinEap = providers.systemProperty("org.gradle.unsafe.kotlin-eap").map(String::toBoolean).orElse(false)
val kotlinVersion = if (useKotlinEap.get()) {
    println("Using Kotlin EAP version to build Gradle")
    "1.3.70-eap-16"
} else {
    null
}

dependencies {

    implementation(project(":configuration"))
    implementation(project(":build"))

    api(kotlin("gradle-plugin", kotlinVersion))
    api(kotlin("stdlib-jdk8", kotlinVersion))
    api(kotlin("reflect", kotlinVersion))
    api(kotlin("compiler-embeddable", kotlinVersion))

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.4.1")
    implementation("com.gradle.publish:plugin-publish-plugin:0.10.0")

    implementation("com.thoughtworks.qdox:qdox:2.0-M9")
    implementation("org.ow2.asm:asm:7.1")

    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
}
