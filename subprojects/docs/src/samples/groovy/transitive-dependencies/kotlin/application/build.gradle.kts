plugins {
    groovy
    application
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.5.7")
    implementation(project(":utilities"))
}

application {
    mainClassName = "org.gradle.sample.Main"
}