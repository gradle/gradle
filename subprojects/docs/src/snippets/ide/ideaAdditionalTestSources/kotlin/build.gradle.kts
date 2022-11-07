plugins {
    idea
    `java-library`
}

// tag::mark-additional-sourcesets-as-test[]
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
// end::mark-additional-sourcesets-as-test[]
