plugins {
    id("org.springframework.boot") version("2.2.1.RELEASE")
    id("io.spring.dependency-management") version("1.0.8.RELEASE")
    java
}

version = "1.0.2"
group = "org.gradle.samples"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(mapOf("group" to "org.junit.vintage", "module" to "junit-vintage-engine"))
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
