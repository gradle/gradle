apply(plugin = "org.gradle.kotlin.kotlin-dsl")

dependencies {
    api(project(":integrationTesting"))
    implementation(project(":build"))
    implementation("org.openmbee.junit:junit-xml-parser:1.0.0")
    implementation("org.codehaus.groovy.modules.http-builder:http-builder:0.7.2") {
        // Xerces on the runtime classpath is breaking some of our doc tasks
        exclude(group = "xerces")
    }
}

tasks.withType<Test>().configureEach {
    if (JavaVersion.current().isJava9Compatible) {
        jvmArgs("--add-modules", "java.xml.bind")
    }
}
