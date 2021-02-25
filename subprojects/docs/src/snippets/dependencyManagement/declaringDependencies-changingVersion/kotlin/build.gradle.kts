// tag::dependencies[]
plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/snapshot/")
    }
}

dependencies {
    implementation("org.springframework:spring-web:5.0.3.BUILD-SNAPSHOT")
}
// end::dependencies[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into(layout.buildDirectory.dir("libs"))
}
