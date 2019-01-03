plugins {
    java
    idea
}

group = "org.sample"
version = "1.0"

repositories {
    ivy {
        name = "localrepo"
        url = uri(file("../../../local-repo"))
    }
}

tasks.named<Upload>("uploadArchives") {
    repositories {
        add(project.repositories["localrepo"])
    }
}
