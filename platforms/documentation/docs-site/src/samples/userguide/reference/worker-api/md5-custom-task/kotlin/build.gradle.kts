plugins { id("base") } // <1>

tasks.register<CreateMD5>("md5") {
    destinationDirectory = project.layout.buildDirectory.dir("md5") // <2>
    source(project.layout.projectDirectory.file("src")) // <3>
}
