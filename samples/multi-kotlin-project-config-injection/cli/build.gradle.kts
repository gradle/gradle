plugins {
    application
    kotlin("jvm")
}

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
    compile(kotlin("stdlib"))
}
