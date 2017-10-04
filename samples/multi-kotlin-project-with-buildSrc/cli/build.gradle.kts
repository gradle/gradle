plugins {
    application
    kotlin("jvm") version "1.1.51"
}

kotlinProject()

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
}
