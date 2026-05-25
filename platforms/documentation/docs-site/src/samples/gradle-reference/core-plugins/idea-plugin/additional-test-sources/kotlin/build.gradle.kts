plugins {
    idea
    `java-library`
}

sourceSets {
    create("intTest") {
        java {
            setSrcDirs(listOf("src/integration"))
        }
    }
}

idea {
    module {
        testSources.from(sourceSets["intTest"].java.srcDirs)
    }
}
