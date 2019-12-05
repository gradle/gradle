plugins {
    groovy
    `maven-publish`
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    jcenter()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.5.7")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components.getByName("java"))
        }
    }
    repositories {
        maven {
            url = uri("${buildDir}/publishing-repository")
        }
    }
}
