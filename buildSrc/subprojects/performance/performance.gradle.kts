apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    api(project(":integrationTesting"))
    implementation(project(":build"))
    implementation("org.openmbee.junit:junit-xml-parser:1.0.0")
    implementation("org.codehaus.groovy.modules.http-builder:http-builder:0.7.2") {
        // Xerces on the runtime classpath is breaking some of our doc tasks
        exclude(group = "xerces")
    }
    if (JavaVersion.current().isJava9Compatible) {
        // Java 9 throws ClassNotFoundException
        implementation("javax.activation:activation:1.1.1")
        // only high version of lombok supports Java 9
        implementation("org.projectlombok:lombok:1.18.2")
    }
}
