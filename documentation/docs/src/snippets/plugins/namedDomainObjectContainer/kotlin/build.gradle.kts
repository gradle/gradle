plugins {
    id("org.gradle.sample.download")
}

download {
    // Can use a block to configure the container contents
    resources {
        register("gradle") {
            uri.set(uri("https://gradle.org"))
        }
    }
}
