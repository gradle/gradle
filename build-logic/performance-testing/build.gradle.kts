plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides a plugin for generating and defining performance test projects"

dependencies {
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(projects.cleanup)
    implementation(projects.buildUpdateUtils)
    implementation(projects.integrationTesting)
    implementation(projects.jvm)

    implementation(buildLibs.guava)
    implementation(buildLibs.gson)
    implementation(buildLibs.commonsIo)
    implementation(buildLibs.jakartaXml)
    implementation(buildLibs.develocityPlugin)

    // https://eclipse-ee4j.github.io/jaxb-ri/
    runtimeOnly(buildLibs.jaxb)

    testImplementation(testLibs.junitJupiter)
    testImplementation(testLibs.junit)

    testRuntimeOnly(testLibs.junitPlatform)
}

gradlePlugin {
    plugins {
        register("performanceTest") {
            id = "gradlebuild.performance-testing"
            implementationClass = "gradlebuild.performance.PerformanceTestPlugin"
        }
    }
}

tasks.compileGroovy.configure {
    classpath = sourceSets.main.get().compileClasspath
}

tasks.compileKotlin.configure {
    libraries.from(files(tasks.compileGroovy))
}

tasks.codenarcMain.configure {
    exclude("gradlebuild/performance/junit4/**")
}
