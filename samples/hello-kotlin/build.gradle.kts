plugins {
    application
    kotlin("jvm") version "1.2.60-eap-74"
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
