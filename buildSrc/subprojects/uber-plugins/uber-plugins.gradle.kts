import org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins

plugins {
    `java-gradle-plugin`
}

apply {
    plugin("org.gradle.kotlin.kotlin-dsl")
    plugin<PrecompiledScriptPlugins>()
}

dependencies {
    implementation(project(":binaryCompatibility"))
    implementation(project(":cleanup"))
    implementation(project(":configuration"))
    implementation(project(":kotlinDsl"))
    implementation(project(":profiling"))
    implementation(project(":plugins"))
}
