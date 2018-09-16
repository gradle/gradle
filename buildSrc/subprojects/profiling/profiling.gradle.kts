import org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins

plugins {
    `java-gradle-plugin`
}

apply(plugin = "org.gradle.kotlin.kotlin-dsl")
apply<PrecompiledScriptPlugins>()

dependencies {
    implementation("me.champeau.gradle:jmh-gradle-plugin:0.4.7")
    implementation("org.jsoup:jsoup:1.11.3")
    implementation("com.gradle:build-scan-plugin:1.16")
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
}

gradlePlugin {
    plugins {
        register("buildscan") {
            id = "gradlebuild.buildscan"
            implementationClass = "org.gradle.gradlebuild.profiling.buildscan.BuildScanPlugin"
        }
    }
}
