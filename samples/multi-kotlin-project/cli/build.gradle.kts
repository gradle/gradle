plugins {
    kotlin("jvm")
    application
}

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
    compile(kotlin("stdlib"))
}
