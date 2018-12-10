// tag::public-repository[]
repositories {
    jcenter()
}
// end::public-repository[]

val libs by configurations.creating

dependencies {
    libs("com.google.guava:guava:23.0")
}

tasks.register<Copy>("copyLibs") {
    from(libs)
    into("$buildDir/libs")
}
