
dependencies {

    implementation(project(":build"))
    implementation(project(":configuration"))

    api(kotlin("gradle-plugin"))
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api(kotlin("compiler-embeddable"))

    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.4.1")
    implementation("com.gradle.publish:plugin-publish-plugin:0.10.0")

    implementation("com.thoughtworks.qdox:qdox:2.0-M9")
    implementation("org.ow2.asm:asm:7.1")

    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
}
