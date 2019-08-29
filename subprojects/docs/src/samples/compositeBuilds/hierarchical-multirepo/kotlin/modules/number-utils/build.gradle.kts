plugins {
    java
    `ivy-publish`
}

group = "org.sample"
version = "1.0"

publishing {
    repositories {
        ivy {
            name = "localrepo"
            url = uri(file("../../../local-repo"))
        }
    }
    publications {
        create<IvyPublication>("ivy") {
            from(components["java"])
        }
    }
}
