plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "gradlebuild"

dependencies {
    compileOnly(localGroovy())
    compileOnly("org.codenarc:CodeNarc:1.5") {
        exclude(group = "org.codehaus.groovy")
    }
}
