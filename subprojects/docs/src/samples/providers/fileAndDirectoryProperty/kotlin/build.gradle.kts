open class FooExtension(objects: ObjectFactory, layout: ProjectLayout) {
    // A directory
    val someDirectory: DirectoryProperty = objects.directoryProperty()
    //  A file
    val someFile: RegularFileProperty = objects.fileProperty()
    // A collection of files or directories
    val someFiles: ConfigurableFileCollection = layout.configurableFiles()
}

project.extensions.create("foo", FooExtension::class, project.objects, project.layout)

configure<FooExtension> {
    // Configure the locations
    someDirectory.set(project.layout.projectDirectory.dir("some-directory"))
    someFile.set(project.layout.buildDirectory.file("some-file"))
    someFiles.from(someDirectory, someFile)
}

task("print") {
    doLast {
        val foo = project.the<FooExtension>()
        val someDirectory = foo.someDirectory.get().asFile
        logger.quiet("foo.someDirectory = " + someDirectory)
        logger.quiet("foo.someFiles contains someDirectory? " + foo.someFiles.contains(someDirectory))

        val someFile = foo.someFile.get().asFile
        logger.quiet("foo.someFile = " + someFile)
        logger.quiet("foo.someFiles contains someFile? " + foo.someFiles.contains(someFile))
    }
}
