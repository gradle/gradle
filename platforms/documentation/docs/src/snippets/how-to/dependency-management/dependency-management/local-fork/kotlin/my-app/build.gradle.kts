plugins {
    id("application")
}

group = "org.sample"
version = "1.0"

repositories {
    mavenCentral()
}

application {
    mainClass = "org.sample.myapp.Main"
}

// tag::dependency[]
dependencies {
    implementation("com.squareup.okhttp:okhttp:2.7.5")
}
// end::dependency[]

/*
// tag::dependency-tip[]
repositories {
    mavenCentral() // Don't remove this!
}

dependencies {
    implementation("com.squareup.okhttp:okhttp:2.7.5") // This doesn't need to change!
}
// end::dependency-tip[]
*/
