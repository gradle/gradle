plugins {
    java
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    jcenter()
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
