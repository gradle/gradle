// tag::do-this[]
plugins {
    id("java-library")
    id("maven-publish")
}

// tag::ok[]
tasks.named<JavaCompile>(JavaPlugin.COMPILE_JAVA_TASK_NAME) { // <1>
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
// end::ok[]

// tag::best[]
java { // <1>
    setSourceCompatibility(JavaVersion.VERSION_17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom.url = "sample.gradle.org" // <2>
        }
    }
}
// end::best[]

version = "1.0.0"
group = "org.gradle.sample"
