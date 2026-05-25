plugins {
    id("java")
}

// tag::local-repos[]
repositories {
    mavenLocal()
    maven {
        setUrl("/path/to/local/repo")
    }
    flatDir {
        dirs("libs")
    }
}
// end::local-repos[]
