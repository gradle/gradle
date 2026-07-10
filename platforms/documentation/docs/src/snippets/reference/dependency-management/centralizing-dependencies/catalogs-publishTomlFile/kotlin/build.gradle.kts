// tag::publish_toml_file[]
plugins {
    `version-catalog`
    `maven-publish`
}

catalog {
    versionCatalog {
        from(files("gradle/libs.versions.toml"))
    }
}

group = "com.mycompany"
version = "1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["versionCatalog"])
        }
    }
}
// end::publish_toml_file[]

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
