plugins {
    id("java")
}

// tag::maven-local-filter[]
repositories {
    mavenLocal {
        content {
            includeGroup("com.example.myproject")
        }
    }
}
// end::maven-local-filter[]
