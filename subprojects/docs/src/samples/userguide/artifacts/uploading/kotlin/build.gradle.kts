plugins {
    java
}

// tag::archive-artifact[]
val myJar by tasks.registering(Jar::class)

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
val myTask by tasks.registering(MyTaskType::class) {
    destFile = file("$buildDir/somefile.txt")
}

artifacts {
    add("archives", myTask.map { it -> it.destFile }) {
        name = "my-artifact"
        type = "text"
        builtBy(myTask)
    }
}
// end::customized-file-artifact[]

// tag::map-file-artifact[]
val generate by tasks.registering(MyTaskType::class) {
    destFile = file("$buildDir/somefile.txt")
}

artifacts {
    add("archives",
        mapOf("file" to generate.get().destFile, "name" to "my-artifact", "type" to "text", "builtBy" to generate))
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

tasks.named<Upload>("uploadArchives") {
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
