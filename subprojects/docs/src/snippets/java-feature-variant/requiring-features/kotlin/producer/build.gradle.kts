plugins {
    `java-library`
}

repositories {
    jcenter()
}

// tag::producer[]
java {
    registerFeature("mysqlSupport") {
        usingSourceSet(sourceSets["main"])
    }
}

dependencies {
    "mysqlSupportImplementation"("mysql:mysql-connector-java:8.0.14")
}
// end::producer[]
