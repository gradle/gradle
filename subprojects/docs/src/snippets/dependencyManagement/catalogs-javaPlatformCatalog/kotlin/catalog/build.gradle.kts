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
    `java-platform`
    `version-catalog`
    `maven-publish`
}

dependencies {
    constraints {
        // the following constraints will be automatically exported to the catalog
        api("com.google.guava:guava:28.0-jre")
        api("org.codehaus.groovy:groovy:3.0.5")
        // ...
    }
}
// end::catalog_definition[]

// tag::explicit_alias[]
catalog {
    configureExplicitAlias("myGroovy", "org.codehaus.groovy", "groovy")
}
// end::explicit_alias[]

group = "com.mycompany"
version = "1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
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
