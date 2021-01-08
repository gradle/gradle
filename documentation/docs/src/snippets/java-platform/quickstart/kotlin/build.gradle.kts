// tag::use-plugin[]
plugins {
    `java-platform`
}
// end::use-plugin[]


// tag::repo[]
repositories {
    mavenCentral()
}
// end::repo[]

// tag::constraints[]
dependencies {
    constraints {
        api("commons-httpclient:commons-httpclient:3.1")
        runtime("org.postgresql:postgresql:42.2.5")
    }
}
// end::constraints[]

// tag::platform[]
// tag::allow-dependencies[]
javaPlatform {
    allowDependencies()
}
// end::allow-dependencies[]

dependencies {
    api(platform("com.fasterxml.jackson:jackson-bom:2.9.8"))
}
// end::platform[]
