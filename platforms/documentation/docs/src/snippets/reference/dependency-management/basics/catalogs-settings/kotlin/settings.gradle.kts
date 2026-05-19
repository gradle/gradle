/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

rootProject.name = "catalog"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

if (providers.systemProperty("create1").getOrNull() != null) {
    // tag::simple_catalog[]
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                library("groovy-core", "org.codehaus.groovy:groovy:3.0.5")
                library("groovy-json", "org.codehaus.groovy:groovy-json:3.0.5")
                library("groovy-nio", "org.codehaus.groovy:groovy-nio:3.0.5")
                library("commons-lang3", "org.apache.commons", "commons-lang3").version {
                    strictly("[3.8, 4.0[")
                    prefer("3.9")
                }
            }
        }
    }
    // end::simple_catalog[]
}

if (providers.systemProperty("create2").getOrNull() != null) {
    // tag::catalog_with_versions[]
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                version("groovy", "3.0.5")
                version("checkstyle", "8.37")
                library("groovy-core", "org.codehaus.groovy", "groovy").versionRef("groovy")
                library("groovy-json", "org.codehaus.groovy", "groovy-json").versionRef("groovy")
                library("groovy-nio", "org.codehaus.groovy", "groovy-nio").versionRef("groovy")
                library("commons-lang3", "org.apache.commons", "commons-lang3").version {
                    strictly("[3.8, 4.0[")
                    prefer("3.9")
                }
            }
        }
    }
    // end::catalog_with_versions[]
}

if (providers.systemProperty("create3").getOrNull() != null) {
    // tag::catalog_with_bundle[]
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                version("groovy", "3.0.5")
                version("checkstyle", "8.37")
                library("groovy-core", "org.codehaus.groovy", "groovy").versionRef("groovy")
                library("groovy-json", "org.codehaus.groovy", "groovy-json").versionRef("groovy")
                library("groovy-nio", "org.codehaus.groovy", "groovy-nio").versionRef("groovy")
                library("commons-lang3", "org.apache.commons", "commons-lang3").version {
                    strictly("[3.8, 4.0[")
                    prefer("3.9")
                }
                bundle("groovy", listOf("groovy-core", "groovy-json", "groovy-nio"))
            }
        }
    }
    // end::catalog_with_bundle[]
}

if (providers.systemProperty("create4").getOrNull() != null) {
    // tag::catalog_with_plugin[]
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                plugin("versions", "com.github.ben-manes.versions").version("0.45.0")
            }
        }
    }
    // end::catalog_with_plugin[]
    dependencyResolutionManagement {
        versionCatalogs {
            named("libs") {
                version("groovy", "3.0.5")
                version("checkstyle", "8.37")
                library("groovy-core", "org.codehaus.groovy", "groovy").versionRef("groovy")
                library("groovy-json", "org.codehaus.groovy", "groovy-json").versionRef("groovy")
                library("groovy-nio", "org.codehaus.groovy", "groovy-nio").versionRef("groovy")
                library("commons-lang3", "org.apache.commons", "commons-lang3").version {
                    strictly("[3.8, 4.0[")
                    prefer("3.9")
                }
                bundle("groovy", listOf("groovy-core", "groovy-json", "groovy-nio"))
            }
        }
    }
}

// tag::extra_catalog[]
dependencyResolutionManagement {
    versionCatalogs {
        create("testLibs") {
            val junit5 = version("junit5", "5.7.1")
            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef(junit5)
            library("junit-engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef(junit5)
        }
    }
}
// end::extra_catalog[]
