subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
    }

    configure<PublishingExtension> {
        repositories {
            maven {
                url = uri("${rootProject.buildDir}/repo") // change to point to your repo, e.g. http://my.org/repo
            }
        }
    }
}

project(":project1") {
    dependencies {
        "api"("org.slf4j:slf4j-api:1.7.10")
    }

    // tag::customize-identity[]
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "org.gradle.sample"
                artifactId = "project1-sample"
                version = "1.1"

                from(components["java"])
            }
        }
    }
    // end::customize-identity[]
}

project(":project2") {
    dependencies {
        "implementation"("commons-collections:commons-collections:3.2.2")
        "api"(project(":project1"))
    }

    // tag::multiple-publications[]
    group = "org.gradle.sample"
    version = "2.3"

    tasks.register<Jar>("apiJar") {
        archiveBaseName.set("project2-api")
        from(sourceSets.main.get().output)
        exclude("**/impl/**")
    }

    publishing {
        publications {
            create<MavenPublication>("impl") {
                artifactId = "project2-impl"

                from(components["java"])
            }
            create<MavenPublication>("api") {
                artifactId = "project2-api"

                artifact(tasks["apiJar"])
            }
        }
    }
    // end::multiple-publications[]
}

fun Project.publishing(action: PublishingExtension.() -> Unit) =
    configure<PublishingExtension>(action)

val Project.sourceSets
    get() = the<SourceSetContainer>()

val SourceSetContainer.main
    get() = named("main")
