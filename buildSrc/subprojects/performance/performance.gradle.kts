apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    api(project(":integrationTesting"))
    implementation(project(":build"))
    implementation("org.codehaus.groovy.modules.http-builder:http-builder:0.7.2") {
        // Xerces on the runtime classpath is breaking some of our doc tasks
        exclude(group = "xerces")
    }
    implementation("org.openmbee.junit:junit-xml-parser:1.0.0") {
        // don't need it at runtime
        exclude(module = "lombok")
    }
    implementation("commons-io:commons-io:2.6")
    implementation("javax.activation:activation:1.1.1")
    implementation("javax.xml.bind:jaxb-api:2.2.12")
}
