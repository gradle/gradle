import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {

    val pluginsExperiments by extra(
        "gradle.plugin.org.gradle.kotlin:gradle-kotlin-dsl-plugins-experiments:0.1.15"
    )

    dependencies {
        classpath(pluginsExperiments)
    }

    configure(listOf(repositories, project.repositories)) {
        gradlePluginPortal()
    }
}

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

apply(plugin = "org.gradle.kotlin.ktlint-convention")

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-Xjsr305=strict",
            "-Xskip-runtime-version-check",
            "-Xskip-metadata-version-check"
        )
    }
}

val pluginsExperiments: String by extra

dependencies {
    compile(pluginsExperiments)

    compileOnly(gradleKotlinDsl())

    compile(kotlin("gradle-plugin"))
    compile(kotlin("stdlib-jdk8"))
    compile(kotlin("reflect"))

    compile("com.gradle.publish:plugin-publish-plugin:0.10.0")
    compile("org.ow2.asm:asm:6.2.1")

    testCompile("junit:junit:4.12")
    testCompile(gradleTestKit())
}
