plugins {
    groovy
    `maven-publish`
}

// tag::gradle-api-dependencies[]
// tag::local-groovy-dependencies[]
dependencies {
// end::local-groovy-dependencies[]
    implementation(gradleApi())
// end::gradle-api-dependencies[]
// tag::local-groovy-dependencies[]
    implementation(localGroovy())
// tag::gradle-api-dependencies[]
}
// end::gradle-api-dependencies[]
// end::local-groovy-dependencies[]

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
