plugins {
    application
    kotlin("jvm")
}

kotlinProject()

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
}
