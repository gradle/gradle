plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::get-recommendations[]
dependencies {
    // A local platform
    api(platform(project(":platform"))) // get recommended versions from the platform project
    api("commons-httpclient:commons-httpclient") // no version required in declaration

    // A published platform
    implementation(platform("org.junit:junit-bom:5.10.0")) // get recommended versions from the platform project
    testImplementation("org.junit.jupiter:junit-jupiter") // no version required in declaration
}
// end::get-recommendations[]

