// tag::public-repository[]
repositories {
    jcenter()
}
// end::public-repository[]

val libs by configurations.creating

dependencies {
    libs("com.google.guava:guava:23.0")
}

task<Copy>("copyLibs") {
    from(libs)
    into("$buildDir/libs")
}
