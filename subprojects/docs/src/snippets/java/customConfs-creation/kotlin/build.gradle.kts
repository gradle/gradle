plugins {
    `java-library`
}

// tag::create-configurations[]
configurations {
    val myCustomConfiguration: Configuration by creating
    myCustomConfiguration.extendsFrom(configurations["implementation"])
}
// end::create-configurations[]
