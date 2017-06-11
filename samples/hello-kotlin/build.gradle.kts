plugins {
    application
    kotlin("jvm")
}

application {
    mainClassName = "samples.HelloWorldKt"
}

repositories {
    gradleScriptKotlin()
}

dependencies {
    compile(kotlin("stdlib"))
}
