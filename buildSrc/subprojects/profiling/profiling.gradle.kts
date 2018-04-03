plugins {
    `java-gradle-plugin`
}

apply { plugin("org.gradle.kotlin.kotlin-dsl") }

dependencies {
    implementation("me.champeau.gradle:jmh-gradle-plugin:0.4.5")
    implementation("org.jsoup:jsoup:1.11.2")
    implementation("com.gradle:build-scan-plugin:1.13-rc-1-20180316150839-master")
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
}

gradlePlugin {
    (plugins) {
        "buildscan" {
            id = "gradlebuild.buildscan"
            implementationClass = "org.gradle.gradlebuild.profiling.buildscan.BuildScanPlugin"
        }
        "jhm" {
            id = "gradlebuild.jmh"
            implementationClass = "org.gradle.gradlebuild.profiling.JmhPlugin"
        }
    }
}


