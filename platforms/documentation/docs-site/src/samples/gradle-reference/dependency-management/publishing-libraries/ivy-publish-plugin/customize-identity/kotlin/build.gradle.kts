plugins {
    java
    `ivy-publish`
}

publishing {
    repositories {
        ivy {
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
    publications {
        create<IvyPublication>("ivy") {
            organisation = "org.gradle.sample"
            module = "project1-sample"
            revision = "1.1"
            descriptor.status = "milestone"
            descriptor.branch = "testing"
            descriptor.extraInfo("http://my.namespace", "myElement", "Some value")

            from(components["java"])
        }
    }
}
