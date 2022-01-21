plugins {
    id("java-library")
}
description = "Provides a custom CodeNarc rule used by the Gradle build"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

group = "gradlebuild"

dependencies {
    compileOnly(localGroovy())
    compileOnly("org.codenarc:CodeNarc:2.0.0") {
        exclude(group = "org.codehaus.groovy")
    }
}
