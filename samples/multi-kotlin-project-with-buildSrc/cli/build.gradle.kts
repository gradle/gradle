buildscript {
    repositories {
        gradleScriptKotlin()
    }
    dependencies {
        classpath(kotlinModule("gradle-plugin"))
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
