plugins {
    java
    idea
    `ivy-publish`
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


publishing {
    repositories {
        ivy {
            setUrl(file("../../../local-repo"))
        }
    }
    publications {
        create<IvyPublication>("ivy") { from(components["java"]) }
    }
}