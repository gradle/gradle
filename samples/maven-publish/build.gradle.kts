import org.gradle.api.tasks.bundling.Jar

plugins {
    kotlin("jvm") version "1.2.60-eap-74"
    `maven-publish`
}

group = "org.gradle.sample"
version = "1.0.0"

repositories {
    jcenter()
}

dependencies {
    compile(kotlin("stdlib"))
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(sourceSets["main"].allSource)
}

publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("$buildDir/repo")
        }
    }
    (publications) {
        "mavenJava"(MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}
