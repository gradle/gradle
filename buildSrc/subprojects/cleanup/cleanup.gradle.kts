apply { plugin("org.gradle.kotlin.kotlin-dsl")}

dependencies {
    implementation(project(":configuration"))
    implementation(project(":testing"))
    implementation(project(":kotlinDsl"))
}
