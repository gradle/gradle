plugins {
    id("application")
}

// tag::package-app[]
val packageApp = tasks.register<Zip>("packageApp") {
    from(layout.projectDirectory.file("run.sh"))                // input - run.sh file
    from(tasks.jar) {                                           // input - jar task output
        into("libs")
    }
    from(configurations.runtimeClasspath) {                     // input - jar of dependencies
        into("libs")
    }
    destinationDirectory.set(layout.buildDirectory.dir("dist")) // output - location of the zip file
    archiveFileName.set("myApplication.zip")                    // output - name of the zip file
}
// end::package-app[]

// tag::wire-lifecycle[]
tasks.build {
    dependsOn(packageApp)
}
// end::wire-lifecycle[]
