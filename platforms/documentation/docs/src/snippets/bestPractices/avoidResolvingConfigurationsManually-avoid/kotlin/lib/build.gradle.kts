plugins {
    `java-library`
}

// tag::avoid-this[]
tasks.named("compileJava").configure {
    doLast {
        logger.lifecycle("Lib was compiled")
    }
}
// end::avoid-this[]
