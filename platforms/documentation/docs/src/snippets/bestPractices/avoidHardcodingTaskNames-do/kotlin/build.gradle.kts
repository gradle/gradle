// tag::do-this[]
plugins {
    id("java-library")
}

java { // <1>
    setSourceCompatibility(JavaVersion.VERSION_17)
}

tasks.withType<Test>().configureEach { // <2>
    useJUnit()
}
// end::do-this[]

dependencies {
    testImplementation("junit:junit:4.13.2")
}
