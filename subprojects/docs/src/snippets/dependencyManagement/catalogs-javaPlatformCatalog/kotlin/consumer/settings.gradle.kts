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

rootProject.name = "consumer"

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("${file("../catalog/repo")}")
        }
        mavenCentral()
    }
}

if (providers.systemProperty("create1").forUseAtConfigurationTime().getOrNull() != null) {
    // tag::consume_catalog[]
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                from("com.mycompany:catalog:1.0")
            }
        }
    }
    // end::consume_catalog[]
}

if (providers.systemProperty("create2").forUseAtConfigurationTime().getOrNull() != null) {
    // tag::overwrite_version[]
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                from("com.mycompany:catalog:1.0")
                // overwrite the "groovy" version declared in the imported catalog
                version("groovy", "3.0.6")
            }
        }
    }
    // end::overwrite_version[]
}

if (providers.systemProperty("compose1").forUseAtConfigurationTime().getOrNull() != null) {
    // tag::compose_catalog[]
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                from("com.mycompany:catalog:1.0")
                from("com.other:catalog:1.1")
                // and add explicit dependencies
                alias("my-alias").to("my.own:lib:1.2")
            }
        }
    }
    // end::compose_catalog[]
}

if (providers.systemProperty("compose2").forUseAtConfigurationTime().getOrNull() != null) {
    // tag::compose_catalog_filtering[]
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs") {
                from("com.mycompany:catalog:1.0") {
                    // import everything, excluding the following dependency
                    excludeDependency("some-alias")
                }
                from("com.other:catalog:1.1") {
                    // exclude everything but this dependency
                    includeDependency("some-dep")
                }
                // and add explicit dependencies
                alias("some-alias").to("my.own:lib:1.2")
            }
        }
    }
    // end::compose_catalog_filtering[]
}
