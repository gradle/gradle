plugins {
    groovy
    `maven-publish`
}

version.set("1.0.2")
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:3.0.9")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("${buildDir}/publishing-repository")
        }
    }
}
