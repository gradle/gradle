kotlinDslPlugin {
    description = "Provides a plugin for generating and defining performance test projects"

    dependencies {
        implementation("gradlebuild:basics")
        implementation("gradlebuild:module-identity")

        implementation(project(":cleanup"))
        implementation(project(":build-update-utils"))
        implementation(project(":integration-testing"))
        implementation(project(":jvm"))

        implementation(catalog("buildLibs.guava"))
        implementation(catalog("buildLibs.gson"))
        implementation(catalog("buildLibs.commonsIo"))
        implementation(catalog("buildLibs.jakartaXml"))
        implementation(catalog("buildLibs.develocityPlugin"))

        // https://eclipse-ee4j.github.io/jaxb-ri/
        runtimeOnly(catalog("buildLibs.jaxb"))

        testImplementation(catalog("testLibs.junitJupiter"))
        testImplementation(catalog("testLibs.junit"))

        testRuntimeOnly(catalog("testLibs.junitPlatform"))
    }

    gradlePlugins {
        gradlePlugin("performanceTest") {
            id = "gradlebuild.performance-testing"
            implementationClass = "gradlebuild.performance.PerformanceTestPlugin"
        }
    }
}
