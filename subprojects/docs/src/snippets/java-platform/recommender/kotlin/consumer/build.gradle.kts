plugins {
    `java-library`
}

repositories {
    jcenter()
}

// tag::get-recommendations[]
dependencies {
    // get recommended versions from the platform project
    api(platform(project(":platform")))
    // no version required
    api("commons-httpclient:commons-httpclient")
}
// end::get-recommendations[]

