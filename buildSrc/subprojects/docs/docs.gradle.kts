apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation("org.pegdown:pegdown:1.6.0")
    implementation("com.uwyn:jhighlight:1.0") {
        exclude(module = "servlet-api")
    }
}
