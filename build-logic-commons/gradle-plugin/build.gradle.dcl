kotlinBuildLogic {
    description = "Provides plugins used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

    dependencies {
        compileOnly(catalog("buildLibs.develocityPlugin"))

        api(platform(project(":build-platform")))

        implementation(project(":basics"))
        implementation(project(":module-identity"))
        implementation(catalog("buildLibs.errorPronePlugin"))
        implementation(catalog("buildLibs.nullawayPlugin"))
        implementation(kotlinDlsGradlePlugin())
        implementation(catalog("buildLibs.kgp"))
        implementation(catalog("buildLibs.testRetryPlugin"))
        implementation(catalog("buildLibs.detektPlugin"))
    }
}
