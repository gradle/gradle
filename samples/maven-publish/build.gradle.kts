plugins {
    `maven-publish`
    kotlin("jvm")
}

group = "org.gradle.sample"
version = "1.0.0"

repositories {
    jcenter()
}

dependencies {
    compile(kotlin("stdlib"))
}

publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("$buildDir/repo")
        }
    }

    publications.create("mavenJava", MavenPublication::class.java) {
        from(components["java"])
    }
}

