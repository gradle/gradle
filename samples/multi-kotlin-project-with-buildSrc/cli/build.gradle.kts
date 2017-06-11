buildscript {
    repositories {
        gradleScriptKotlin()
    }
    dependencies {
        classpath(kotlin("gradle-plugin"))
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
