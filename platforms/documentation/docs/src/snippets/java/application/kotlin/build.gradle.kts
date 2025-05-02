// tag::use-plugin[]
plugins {
    application
}
// end::use-plugin[]

version = "1.0.2"

// tag::applicationName-conf[]
application.applicationName = "my-app"
// end::applicationName-conf[]

// tag::mainClass-conf[]
application {
    mainClass = "org.gradle.sample.Main"
}
// end::mainClass-conf[]

// tag::mainModule-conf[]
application {
    mainModule = "org.gradle.sample.app" // name defined in module-info.java
    mainClass = "org.gradle.sample.Main"
}
// end::mainModule-conf[]

// tag::application-defaultjvmargs[]
application {
    applicationDefaultJvmArgs = listOf("-Dgreeting.language=en")
}
// end::application-defaultjvmargs[]

// tag::executableDir-conf[]
application {
    executableDir = "custom_bin_dir"
}
// end::executableDir-conf[]

// tag::distribution-spec[]
val createDocs by tasks.registering {
    val docs = layout.buildDirectory.dir("docs")
    outputs.dir(docs)
    doLast {
        docs.get().asFile.mkdirs()
        docs.get().file("readme.txt").asFile.writeText("Read me!")
    }
}

distributions {
    main {
        contents {
            from(createDocs) {
                into("docs")
            }
        }
    }
}
// end::distribution-spec[]
