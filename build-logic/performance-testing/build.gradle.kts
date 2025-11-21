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

    implementation("com.google.guava:guava")
    implementation("com.google.code.gson:gson")
    implementation("commons-io:commons-io:2.15.1")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api")
    implementation("com.gradle:develocity-gradle-plugin")

    // https://eclipse-ee4j.github.io/jaxb-ri/
    runtimeOnly("com.sun.xml.bind:jaxb-impl")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
