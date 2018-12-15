dependencies {

    implementation(project(":configuration"))

    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.2.0")
    implementation("com.gradle.publish:plugin-publish-plugin:0.10.0")

    implementation("com.thoughtworks.qdox:qdox:2.0-M9")
    implementation("org.ow2.asm:asm:6.2.1")

    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
}
