// tag::repositories[]
plugins {
    `java-library`
}

repositories {
    maven {
        name = "corp-repo"
        // Nothing is served here: .invalid never resolves, so the build only
        // works if the mirror in settings.xml takes its place
        url = uri("https://repo.example.invalid/maven2")
    }
}

dependencies {
    implementation("org.apache.commons:commons-lang3:3.14.0")
}
// end::repositories[]
