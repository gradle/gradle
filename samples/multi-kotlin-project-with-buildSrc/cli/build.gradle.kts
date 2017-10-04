plugins {
    kotlin("jvm")
    application
}

kotlinProject()

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
}
