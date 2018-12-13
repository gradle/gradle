plugins {
    java
    maven
}

group = "org.gradle.kotlin-dsl"

version = "1.0"

tasks {

    // TODO prefer the lazy string invoke once https://github.com/gradle/gradle-native/issues/718 is fixed
    getByName<Upload>("uploadArchives") {

        repositories {

            withConvention(MavenRepositoryHandlerConvention::class) {

                mavenDeployer {

                    withGroovyBuilder {
                        "repository"("url" to uri("$buildDir/m2/releases"))
                        "snapshotRepository"("url" to uri("$buildDir/m2/snapshots"))
                    }

                    pom.project {
                        withGroovyBuilder {
                            "parent" {
                                "groupId"("org.gradle")
                                "artifactId"("kotlin-dsl")
                                "version"("1.0")
                            }
                            "licenses" {
                                "license" {
                                    "name"("The Apache Software License, Version 2.0")
                                    "url"("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                    "distribution"("repo")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
