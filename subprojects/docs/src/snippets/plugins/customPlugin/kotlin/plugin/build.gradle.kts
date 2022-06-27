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
    testImplementation("junit:junit:4.13")
}

group = "org.example"
version = "1.0-SNAPSHOT"

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// Needed when using ProjectBuilder
class AddOpensArgProvider(private val test: Test) : CommandLineArgumentProvider {
    override fun asArguments() : Iterable<String> {
        return if (test.javaVersion.isCompatibleWith(JavaVersion.VERSION_1_9)) {
            listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
        } else {
            emptyList()
        }
    }
}
tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(AddOpensArgProvider(this))
}
