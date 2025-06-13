plugins {
    `java-library`
}

// tag::avoid-this[]
tasks.named("jar").configure {
    doLast {
        logger.lifecycle("jar task was executed")
    }
}
// end::avoid-this[]
