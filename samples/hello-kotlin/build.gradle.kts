plugins {
    application
    kotlin("jvm")
}

application {
    mainClassName = "samples.HelloWorldKt"
}

dependencies {
    compile(kotlin("stdlib"))
}

repositories {
    gradleScriptKotlin()
}
