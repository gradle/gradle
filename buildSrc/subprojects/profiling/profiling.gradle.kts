plugins {
    `java-gradle-plugin`
    id("me.champeau.gradle.jmh").version("0.4.5")
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    compile("me.champeau.gradle:jmh-gradle-plugin:0.4.5")
    compile("org.jsoup:jsoup:1.11.2")
    compile("com.gradle:build-scan-plugin:1.12.1")
    compile(project(":configuration"))
    compile(project(":kotlinDsl"))
}

gradlePlugin {
    (plugins) {
        "buildscanConfiguration" {
            id = "buildscan-configuration"
            implementationClass = "org.gradle.gradlebuild.profiling.buildscan.BuildScanConfigurationPlugin"
        }
        "jhm" {
            id = "jmh"
            implementationClass = "org.gradle.gradlebuild.profiling.JmhPlugin"
        }
    }
}


