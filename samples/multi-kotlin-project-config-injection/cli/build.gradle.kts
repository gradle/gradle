plugins {
    application
}

apply {
    plugin("kotlin")
}

application {
    mainClassName = "cli.Main"
}

dependencies {
    compile(project(":core"))
    compile(kotlin("stdlib"))
}
