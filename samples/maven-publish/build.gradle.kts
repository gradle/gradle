import org.gradle.api.tasks.bundling.Jar

plugins {
    kotlin("jvm") version "1.3.20"
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

val sourcesJar by tasks.registering(Jar::class) {
    classifier = "sources"
    from(sourceSets.main.get().allSource)
}

publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("$buildDir/repo")
        }
    }
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }
}
