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
        testSourceDirs = testSourceDirs + sourceSets["intTest"].java.srcDirs
    }
}
// end::mark-additional-sourcesets-as-test[]
