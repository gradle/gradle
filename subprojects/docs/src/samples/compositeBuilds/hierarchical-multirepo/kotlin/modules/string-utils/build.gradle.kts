plugins {
    java
    idea
}

group = "org.sample"
version = "1.0"

dependencies {
    implementation("org.apache.commons:commons-lang3:3.4")
}

repositories {
    ivy {
        name = "localrepo"
        url = uri(file("../../../local-repo"))
    }
    jcenter()
}

tasks.named<Upload>("uploadArchives") {
    repositories {
        add(project.repositories["localrepo"])
    }
}
