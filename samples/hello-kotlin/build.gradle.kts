buildscript {

    repositories {
        gradleScriptKotlin()
    }

    dependencies {
        classpath(kotlinModule("gradle-plugin"))
    }
}

plugins {
    application
}

apply {
    plugin("kotlin")
}

application {
    mainClassName = "samples.HelloWorldKt"
}

repositories {
    gradleScriptKotlin()
}

dependencies {
    compile(kotlinModule("stdlib"))
}
