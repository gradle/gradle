plugins {
    java
}

interface CustomSourceSetConvention {
    val someOption: Property<String>
}

(sourceSets["main"].extensions as Convention).plugins["custom"] = objects.newInstance<CustomSourceSetConvention>()

// tag::source-set-convention[]
sourceSets {
    main {
        withConvention(CustomSourceSetConvention::class) {
            someOption = "some value"
        }
    }
}
// end::source-set-convention[]
