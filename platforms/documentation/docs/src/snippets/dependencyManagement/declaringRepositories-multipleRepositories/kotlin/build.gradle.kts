// tag::multiple-repositories[]
repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/release")
    }
    maven {
        url = uri("https://repository.jboss.org/maven2")
    }
}
// end::multiple-repositories[]

val libs by configurations.creating

dependencies {
    libs("jboss:jboss-system:4.2.2.GA")
}

tasks.register<Copy>("copyLibs") {
    from(libs)
    into(layout.buildDirectory.dir("libs"))
}
