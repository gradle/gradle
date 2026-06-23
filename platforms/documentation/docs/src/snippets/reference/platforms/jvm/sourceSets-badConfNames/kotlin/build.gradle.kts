plugins {
    `java-library`
}

// tag::create-configurations[]
configurations {
    create("myCodeCompileClasspath")
}

sourceSets {
    create("myCode")
}
// end::create-configurations[]
