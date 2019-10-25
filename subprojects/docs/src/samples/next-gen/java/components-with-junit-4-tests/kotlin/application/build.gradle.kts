plugins {
    application
}

repositories {
    jcenter()
}

dependencies {
    implementation(project(":library"))
    testImplementation("junit:junit:4.12")
}

application {
    mainClassName = "org.gradle.sample.Main"
}