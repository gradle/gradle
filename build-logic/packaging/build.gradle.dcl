kotlinDslPlugin {
    description = "Provides a plugin for building Gradle distributions"

    dependencies {
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")

        implementation(project(":documentation")) {
            because("API metadata generation is part of the DSL guide")
        }
        implementation(project(":jvm"))
        implementation(project(":kotlin-dsl"))

        implementation(catalog("buildLibs.kgp"))

        implementation(catalog("buildLibs.gson"))
        implementation(catalog("libs.asm"))

        testImplementation(catalog("testLibs.junitJupiter"))
    }
}
