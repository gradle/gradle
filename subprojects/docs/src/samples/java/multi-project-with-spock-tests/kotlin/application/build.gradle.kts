plugins {
    application
    `groovy-base`
}

dependencies {
    implementation(project(":utilities"))
    testImplementation("org.codehaus.groovy:groovy-all:2.5.11")
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
}

application {
    mainClass.set("org.gradle.sample.app.Main")
}
