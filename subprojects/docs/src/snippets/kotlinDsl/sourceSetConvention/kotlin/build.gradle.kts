// tag::source-set-convention[]
plugins {
    groovy
}

sourceSets {
    main {
        groovy {
            setSrcDir("src/core/groovy")
        }
    }
}
// end::source-set-convention[]
