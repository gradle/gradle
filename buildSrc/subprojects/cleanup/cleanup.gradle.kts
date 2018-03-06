plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl")}

dependencies {
    compile(project(":configuration"))
    compile(project(":kotlinDsl"))
}

gradlePlugin {
    (plugins) {
        "cleanup" {
            id = "cleanup"
            implementationClass = "org.gradle.cleanup.CleanupPlugin"
        }
    }
}
