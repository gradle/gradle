allprojects {
    group = "com.acme"
    version = "1.0"
}

subprojects {
    apply(plugin = "maven-publish")

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                setUrl("${rootProject.buildDir}/repo")
            }
        }
    }
}
