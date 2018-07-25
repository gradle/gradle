apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    api(project(":integrationTesting"))
    implementation(project(":build"))
    implementation("org.codehaus.groovy.modules.http-builder:http-builder:0.7.2") {
        // Xerces on the runtime classpath is breaking some of our doc tasks
        exclude(group = "xerces")
    }
}
