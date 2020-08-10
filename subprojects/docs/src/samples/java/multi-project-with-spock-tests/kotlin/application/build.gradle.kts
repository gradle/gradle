plugins {
    application
    `groovy-base`
}

dependencies {
    implementation(project(":utilities"))
    testImplementation("org.codehaus.groovy:groovy-all:3.0.5")
    testImplementation("org.spockframework:spock-core:2.0-M3-groovy-3.0")
}

application {
    mainClass.set("org.gradle.sample.app.Main")
}
