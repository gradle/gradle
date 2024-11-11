// tag::external-task-build[]
plugins {
    groovy
// end::external-task-build[]
    `maven-publish`
// tag::external-task-build[]
}

// tag::gradle-api-dependencies[]
dependencies {
    implementation(gradleApi())
}
// end::gradle-api-dependencies[]
// end::external-task-build[]

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13")
}

group = "org.gradle"
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
        return if (test.javaVersion.get().isCompatibleWith(JavaVersion.VERSION_1_9)) {
            listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
        } else {
            emptyList()
        }
    }
}
tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(AddOpensArgProvider(this))
}
