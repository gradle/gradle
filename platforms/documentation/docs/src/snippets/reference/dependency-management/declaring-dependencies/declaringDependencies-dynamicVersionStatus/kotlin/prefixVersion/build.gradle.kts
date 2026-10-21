plugins {
    id("java-library")
}

repositories {
    maven {
        url = uri("../repo")
    }
}

// tag::prefix-version[]
dependencies {
    implementation("com.example:lib:1.+") {
        attributes {
            attribute(Attribute.of("org.gradle.status", String::class.java), "release")
        }
    }
}
// end::prefix-version[]
