plugins {
    id("java-library")
}

repositories {
    maven {
        url = uri("../repo")
    }
}

// tag::configuration-wide[]
configurations.compileClasspath {
    attributes.attribute(Attribute.of("org.gradle.status", String::class.java), "release")
}

configurations.runtimeClasspath {
    attributes.attribute(Attribute.of("org.gradle.status", String::class.java), "release")
}
// end::configuration-wide[]

dependencies {
    implementation("com.example:lib:[1, 2)")
    implementation("com.example:other:[1, 2)")
}
