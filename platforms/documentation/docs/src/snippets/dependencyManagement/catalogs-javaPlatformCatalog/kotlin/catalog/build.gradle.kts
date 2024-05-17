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

// tag::catalog_definition[]
plugins {
    `version-catalog`
    `maven-publish`
}

catalog {
    versionCatalog {
        library("guava", "com.google.guava:guava:28.0-jre")
        library("myGroovy", "org.codehaus.groovy:groovy:3.0.5")
    }
}

group = "com.mycompany"
version = "1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["versionCatalog"])
        }
    }
}

publishing {
    repositories {
        maven {
            url  = uri("${projectDir}/repo")
        }
    }
}
