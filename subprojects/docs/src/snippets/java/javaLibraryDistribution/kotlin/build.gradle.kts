// tag::use-plugin[]
plugins {
    `java-library-distribution`
}
// end::use-plugin[]

version = "1.0.0"

// tag::name-conf[]
distributions {
    main {
        distributionBaseName = "my-name"
    }
}
// end::name-conf[]

// tag::custom-distribution[]
distributions {
    main {
        distributionBaseName = "my-name"
        contents {
            from("src/dist")
        }
    }
}
// end::custom-distribution[]

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-collections:commons-collections:3.2.2")
}
