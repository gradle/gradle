// tag::configurations[]
plugins {
    `java-library`
}
// end::configurations[]

repositories {
    mavenCentral()
}

// tag::configurations[]
dependencies {
    implementation("org.hibernate:hibernate-core:3.6.7.Final")
    testImplementation("junit:junit:4.+")
    api("com.google.guava:guava:23.0")
}
// end::configurations[]
