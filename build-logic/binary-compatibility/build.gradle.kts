plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
    id("gradlebuild.build-logic.groovy-dsl-gradle-plugin")
}

description = "Provides a plugin for configuring japicmp-gradle-plugin to detect binary incompatible changes"

dependencies {
    api(buildLibs.japiCmpPlugin)

    implementation(projects.dependencyModules)
    implementation("gradlebuild:basics")
    implementation("gradlebuild:module-identity")

    implementation(buildLibs.javaParserCore)
    implementation(buildLibs.gson)
    implementation(buildLibs.guava)
    implementation(buildLibs.javaAssist)
    implementation(buildLibs.kotlinMetadata)
    implementation(buildLibs.jspecify)
    implementation(libs.asm)
    compileOnly(buildLibs.kotlinCompilerEmbeddable)

    testImplementation(buildLibs.jsoup)
    testImplementation(testLibs.junit5JupiterEngine)
}

tasks.compileGroovy.configure {
    classpath += files(tasks.compileKotlin)
}
