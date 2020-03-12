// tag::external-task-build[]
plugins {
    groovy
// end::external-task-build[]
    `maven-publish`
// tag::external-task-build[]
}

dependencies {
    implementation(gradleApi())
}
// end::external-task-build[]

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.12")
}

group = "org.gradle"
version = "1.0-SNAPSHOT"

publishing {
    repositories {
        maven {
            url = uri("$buildDir/repo")
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
