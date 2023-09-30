plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides a plugin for generating and defining performance test projects"

dependencies {
    implementation(project(":module-identity"))
    implementation("gradlebuild:basics")
    implementation(project(":integration-testing"))
    implementation(project(":cleanup"))
    implementation(project(":build-update-utils"))

    implementation("org.openmbee.junit:junit-xml-parser") {
        exclude(module = "lombok") // don't need it at runtime
    }
    implementation("com.google.guava:guava")
    implementation("com.google.code.gson:gson")
    implementation("commons-io:commons-io")
    implementation("javax.activation:activation")
    implementation("javax.xml.bind:jaxb-api")
    implementation("com.gradle:gradle-enterprise-gradle-plugin")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit")
    testImplementation("io.mockk:mockk")
}

gradlePlugin {
    plugins {
        register("performanceTest") {
            id = "gradlebuild.performance-test"
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
