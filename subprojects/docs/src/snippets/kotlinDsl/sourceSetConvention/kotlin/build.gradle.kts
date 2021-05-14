// tag::source-set-convention[]
plugins {
    groovy
}

sourceSets {
    main {
        withConvention(GroovySourceSet::class) {
            groovy.setSrcDirs(listOf("src/core/groovy"))
        }
    }
}
// end::source-set-convention[]
