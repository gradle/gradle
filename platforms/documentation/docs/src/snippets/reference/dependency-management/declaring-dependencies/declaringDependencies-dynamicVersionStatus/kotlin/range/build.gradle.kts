plugins {
    id("java-library")
}

repositories {
    maven {
        url = uri("../repo")
    }
}

// tag::range[]
dependencies {
    implementation("com.example:lib:[1, 2)") {
        attributes {
            attribute(Attribute.of("org.gradle.status", String::class.java), "release")
        }
    }
    implementation("com.example:other:[1, 2)")
}
// end::range[]
