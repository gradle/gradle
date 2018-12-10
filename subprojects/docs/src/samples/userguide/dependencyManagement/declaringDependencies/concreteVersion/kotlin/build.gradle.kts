// tag::dependencies[]
plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework:spring-web:5.0.2.RELEASE")
}
// end::dependencies[]

tasks.register<Copy>("copyLibs") {
    from(configurations.compileClasspath)
    into("$buildDir/libs")
}
