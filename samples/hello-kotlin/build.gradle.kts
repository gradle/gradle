plugins {
    application
    kotlin("jvm") version "1.1.61"
}

application {
    mainClassName = "samples.HelloWorldKt"
}

dependencies {
    compile(kotlin("stdlib"))
}

repositories {
    jcenter()
}
