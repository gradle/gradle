plugins {
    `java-gradle-plugin`
}

apply {
    plugin("org.gradle.kotlin.kotlin-dsl")
    plugin<org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins>()
}

dependencies {
    implementation("me.champeau.gradle:jmh-gradle-plugin:0.4.5")
    implementation("org.jsoup:jsoup:1.11.2")
    implementation("com.gradle:build-scan-plugin:1.13.1")
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
}

gradlePlugin {
    (plugins) {
        "buildscan" {
            id = "gradlebuild.buildscan"
            implementationClass = "org.gradle.gradlebuild.profiling.buildscan.BuildScanPlugin"
        }
    }
}
