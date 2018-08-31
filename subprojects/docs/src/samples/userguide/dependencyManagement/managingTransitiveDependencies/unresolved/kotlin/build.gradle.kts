// tag::unresolved-transitive-dependencies[]
plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("log4j:log4j:1.2.15")
}
// end::unresolved-transitive-dependencies[]
