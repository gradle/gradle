plugins {
    groovy
    application
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    jcenter()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.5.11")
}

application {
    mainClass.set("org.gradle.sample.app.Main")
}
