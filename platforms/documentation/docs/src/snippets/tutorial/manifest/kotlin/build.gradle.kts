plugins {
    java
}
version = "1.0"

// tag::add-to-manifest[]
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Gradle",
            "Implementation-Version" to archiveVersion
        )
    }
}
// end::add-to-manifest[]

// tag::custom-manifest[]
val sharedManifest = java.manifest {
    attributes (
        "Implementation-Title" to "Gradle",
        "Implementation-Version" to version
    )
}

tasks.register<Jar>("fooJar") {
    manifest = java.manifest {
        from(sharedManifest)
    }
}
// end::custom-manifest[]

// tag::merge[]
tasks.register<Jar>("barJar") {
    manifest {
        attributes("key1" to "value1")
        from(sharedManifest, "src/config/basemanifest.txt")
        from(listOf("src/config/javabasemanifest.txt", "src/config/libbasemanifest.txt")) {
            eachEntry(Action<ManifestMergeDetails> {
                if (baseValue != mergeValue) {
                    value = baseValue
                }
                if (key == "foo") {
                    exclude()
                }
            })
        }
    }
}
// end::merge[]

// tag::write[]
tasks.jar { manifest.writeTo(layout.buildDirectory.file("mymanifest.mf")) }
// end::write[]
