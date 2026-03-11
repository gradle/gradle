kotlinDslPlugin {
    description = "Provides plugins to configure Kotlin DSL and patch the Kotlin compiler for use in Kotlin subprojects"

    dependencies {
        implementation("gradlebuild:basics")

        implementation(project(":dependency-modules"))
        implementation(project(":jvm"))
        implementation(project(":kotlin-dsl-shared-runtime"))

        implementation(catalog("buildLibs.kgp"))
        implementation(catalog("buildLibs.kotlinSamWithReceiver"))
        implementation(catalog("libs.asm"))
        implementation(catalog("buildLibs.qdox"))

        testImplementation(catalog("testLibs.junit"))
        testImplementation(catalog("testLibs.mockitoKotlin"))
    }
}
