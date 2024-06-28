plugins {
    `java-library`
    id("com.google.protobuf") version ("0.9.4")
}

group = "perfetto"
version = "0.1"

dependencies {
    implementation("com.google.protobuf:protobuf-javalite:4.26.1")
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.26.1"
    }

    generateProtoTasks.all().configureEach {
        builtins {
            named("java") {
                option("lite")
            }
        }
    }
}
