// tag::source-set-convention[]
plugins {
    groovy
}

sourceSets {
    main {
        withConvention(GroovySourceSet::class) {
            groovy.setSrcDir("src/core/groovy")
        }
    }
}
// end::source-set-convention[]
