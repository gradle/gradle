// tag::multiple-repositories[]
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.springframework.org/release")
    }
    maven {
        url = uri("https://maven.restlet.com")
    }
}
// end::multiple-repositories[]

val libs by configurations.creating

dependencies {
    libs("com.restlet.client:commons:2.0.0")
}

tasks.register<Copy>("copyLibs") {
    from(libs)
    into(layout.buildDirectory.dir("libs"))
}
