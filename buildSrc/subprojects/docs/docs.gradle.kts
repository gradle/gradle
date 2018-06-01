apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation("org.pegdown:pegdown:1.6.0")
}
