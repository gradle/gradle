plugins {
    application
    kotlin("jvm") version "1.1.51"
}

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
    compile(kotlin("stdlib"))
}
