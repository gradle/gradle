// tag::dependency-intro-dep[]
plugins {
    id("java-library")  // <1>
}
// end::dependency-intro-dep[]

// tag::dependency-repo[]
repositories {
    google()
    mavenCentral()
}

// tag::dependency-intro-dep[]

// tag::dependency-intro[]
dependencies {
    implementation("com.google.guava:guava:32.1.2-jre") // <2>
    api("org.apache.juneau:juneau-marshall:8.2.0")      // <3>
}
// end::dependency-intro[]
// end::dependency-repo[]
// end::dependency-intro-dep[]

// tag::dependency-intro-catalog[]
dependencies {
    implementation(libs.guava)
    api(libs.juneau.marshall)
}
// end::dependency-intro-catalog[]
