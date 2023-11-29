plugins {
    `java-library`
}

// tag::create-configurations[]
configurations {
    val myCodeCompileClasspath: Configuration by creating
}

sourceSets {
    val myCode: SourceSet by creating
}
// end::create-configurations[]
