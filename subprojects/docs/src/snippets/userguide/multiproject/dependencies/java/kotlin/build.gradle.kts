subprojects {
    apply(plugin = "java")
    group = "org.gradle.sample"
    version = "1.0"
    repositories {
        mavenCentral()
    }
    dependencies {
        "testImplementation"("junit:junit:4.12")
    }
}

project(":api") {
    dependencies {
        "implementation"(project(":shared"))
    }
}

project(":services:personService") {
    dependencies {
        "implementation"(project(":shared"))
        "implementation"(project(":api"))
    }
}

