plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.12")
}

tasks.validatePlugins {
    enableStricterValidation = true
}
