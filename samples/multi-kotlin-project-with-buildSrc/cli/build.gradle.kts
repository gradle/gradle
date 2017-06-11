buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin"))
    }
    repositories {
        gradleScriptKotlin()
    }
}

kotlinProject()

plugins {
    application
}

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
}
