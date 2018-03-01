apply { plugin("org.gradle.kotlin.kotlin-dsl")}

dependencies {
    "compile"(project(":testing"))
    "compile"(project(":kotlinDsl"))
}
