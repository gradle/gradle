plugins {
    `java-library`
}

repositories {
    ivy { url = uri("${rootProject.projectDir.toURI().toASCIIString()}repo") }
}
