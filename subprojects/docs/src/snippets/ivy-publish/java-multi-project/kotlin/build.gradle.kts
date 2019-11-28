// tag::all[]
subprojects {
    apply(plugin = "java")
    apply(plugin = "ivy-publish")

    version = "1.0"
    group = "org.gradle.sample"

    repositories {
        mavenCentral()
    }
    val java = extensions.getByType<JavaPluginExtension>()
    java.withJavadocJar()
    java.withSourcesJar()
}

project(":project1") {
    description = "The first project"

    dependencies {
        "implementation"("junit:junit:4.12")
        "implementation"(project(":project2"))
    }
}

project(":project2") {
    description = "The second project"

    dependencies {
        "implementation"("commons-collections:commons-collections:3.2.2")
    }
}

subprojects {
    configure<PublishingExtension>() {
        repositories {
            ivy {
                // change to point to your repo, e.g. http://my.org/repo
                url = uri("${rootProject.buildDir}/repo")
            }
        }
        publications {
            create<IvyPublication>("ivy") {
                from(components["java"])
                descriptor.description {
                    text.set(description)
                }
            }
        }
    }
}
// end::all[]
