// tag::publish_catalog[]
plugins {
    `version-catalog`
    `maven-publish`
}

catalog {
    versionCatalog {
        version("kotlin", "2.3.20")
        version("errorProne", "2.28.0")
        library("kotlin-stdlib", "org.jetbrains.kotlin", "kotlin-stdlib").versionRef("kotlin")
        library("errorProne-core", "com.google.errorprone", "error_prone_core").versionRef("errorProne")
    }
}

group = "com.mycompany"
version = "1.0"

publishing {
    publications {
        create<MavenPublication>("catalog") {
            from(components["versionCatalog"])
        }
    }
}
// end::publish_catalog[]

publishing {
    repositories {
        maven {
            url = uri(layout.projectDirectory.dir("repo"))
        }
    }
}
