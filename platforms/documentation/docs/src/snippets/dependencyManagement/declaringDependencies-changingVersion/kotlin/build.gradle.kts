// tag::dependencies[]
plugins {
    `java-library`
}

// tag::repo-intro[]
repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/snapshot/")
    }
}
// end::repo-intro[]

dependencies {
    implementation("org.springframework:spring-web:5.0.3.BUILD-SNAPSHOT")
}
// end::dependencies[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
