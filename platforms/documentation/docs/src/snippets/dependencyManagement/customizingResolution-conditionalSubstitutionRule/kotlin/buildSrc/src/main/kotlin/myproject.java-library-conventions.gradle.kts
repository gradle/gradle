plugins {
    `java-library`
}

repositories {
    ivy { url = uri("file://${rootProject.projectDir}/repo") }
}
