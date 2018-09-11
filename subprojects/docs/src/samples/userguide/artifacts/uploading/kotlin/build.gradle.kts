plugins {
    java
}

// tag::archive-artifact[]
val myJar = task<Jar>("myJar")

artifacts {
    add("archives", myJar)
}
// end::archive-artifact[]

// tag::file-artifact[]
val someFile = file("$buildDir/somefile.txt")

artifacts {
    add("archives", someFile)
}
// end::file-artifact[]

// tag::customized-file-artifact[]
val myTask = task<MyTaskType>("myTask") {
    destFile = file("$buildDir/somefile.txt")
}

artifacts {
    add("archives", myTask.destFile!!) {
        name = "my-artifact"
        type = "text"
        builtBy(myTask)
    }
}
// end::customized-file-artifact[]

// tag::map-file-artifact[]
val generate = task<MyTaskType>("generate") {
    destFile = file("$buildDir/somefile.txt")
}

artifacts {
    add("archives",
        mapOf("file" to generate.destFile, "name" to "my-artifact", "type" to "text", "builtBy" to generate))
}
// end::map-file-artifact[]

open class MyTaskType : DefaultTask() {
    var destFile: File? = null
}

// tag::uploading[]
repositories {
    flatDir {
        name = "fileRepo"
        dirs("repo")
    }
}

tasks.getByName<Upload>("uploadArchives") {
    repositories {
        add(project.repositories["fileRepo"])
        ivy {
            credentials {
                username = "username"
                password = "pw"
            }
            url = uri("http://repo.mycompany.com")
        }
    }
}
// end::uploading[]
