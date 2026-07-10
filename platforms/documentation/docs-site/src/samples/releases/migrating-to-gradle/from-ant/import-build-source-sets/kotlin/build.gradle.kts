sourceSets {
    main {
        java.setSrcDirs(listOf(ant.properties["src.dir"]))
        java.destinationDirectory = file(ant.properties["classes.dir"] ?: layout.buildDirectory.dir("classes"))
    }
}
