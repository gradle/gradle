buildscript {
    dependencies {
        classpath(kotlin("gradle-plugin"))
    }
    repositories {
        jcenter()
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
