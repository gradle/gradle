// tag::use-and-configure-plugin[]
plugins {
    `java-gradle-plugin`
// end::use-and-configure-plugin[]
    `maven-publish`
// tag::use-and-configure-plugin[]
}

// end::use-and-configure-plugin[]
group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13")
}

// tag::use-and-configure-plugin[]
gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "org.example.greeting"
            implementationClass = "org.example.GreetingPlugin"
        }
    }
}
// end::use-and-configure-plugin[]

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
