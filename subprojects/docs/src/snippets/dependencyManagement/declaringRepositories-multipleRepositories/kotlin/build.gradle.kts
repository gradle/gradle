// tag::multiple-repositories[]
repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/release")
    }
    maven {
        url = uri("https://repo.gradle.org/gradle/libs-releases")
    }
}
// end::multiple-repositories[]

val libs by configurations.creating

dependencies {
    libs("org.gradle:gradle-tooling-api:6.0")
}

tasks.register<Copy>("copyLibs") {
    from(libs)
    into("$buildDir/libs")
}
