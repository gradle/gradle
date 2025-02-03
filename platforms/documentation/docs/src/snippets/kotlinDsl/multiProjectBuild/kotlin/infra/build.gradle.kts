// tag::root[]
plugins {
    `java-library`
    id("com.gradleup.shadow")
}
// end::root[]

tasks.shadowJar {
    minimize()
}
