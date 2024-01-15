plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::configuration-definition[]
val smokeTest by configurations.creating {
    extendsFrom(configurations.testImplementation.get())
}

dependencies {
    testImplementation("junit:junit:4.13")
    smokeTest("org.apache.httpcomponents:httpclient:4.5.5")
}
// end::configuration-definition[]

tasks.register<Copy>("copyLibs") {
    from(smokeTest)
    into(layout.buildDirectory.dir("libs"))
}
