plugins {
    `java-library`
}

tasks.jar.configure {
    doLast {
        logger.lifecycle("jar task was executed")
    }
}
