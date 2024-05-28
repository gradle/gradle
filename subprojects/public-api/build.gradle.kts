/*
 * Copyright 2024 the original author or authors.
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

import com.google.common.base.Strings
import gradlebuild.basics.capitalize
import gradlebuild.packaging.transforms.CopyPublicApiClassesTransform
import org.apache.commons.lang.StringUtils

plugins {
    id("gradlebuild.distribution.packaging")
    id("gradlebuild.module-identity")
    id("signing")
    `maven-publish`
}

enum class Filtering {
    PUBLIC_API, ALL
}

val filteredAttribute: Attribute<Filtering> = Attribute.of("org.gradle.apijar.filtered", Filtering::class.java)

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))
    pluginsRuntimeOnly(platform(project(":distributions-full")))

    artifactTypes.getByName("jar") {
        attributes.attribute(filteredAttribute, Filtering.ALL)
    }

    // Filters out classes that are not exposed by our API.
    // TODO Use actual filtering not copying
    registerTransform(CopyPublicApiClassesTransform::class.java) {
        from.attribute(filteredAttribute, Filtering.ALL)
            .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
        to.attribute(filteredAttribute, Filtering.PUBLIC_API)
    }
}

fun registerApiJarTask(name: String, dependencies: NamedDomainObjectProvider<Configuration>) {
    val task = tasks.register<Jar>("jar${name.split("-").map { it.capitalize() }.joinToString("")}") {
        from(dependencies.map { classpath ->
            classpath.incoming.artifactView {
                attributes {
                    attribute(filteredAttribute, Filtering.PUBLIC_API)
                }
                componentFilter { component ->
                    component is ProjectComponentIdentifier &&
                        // FIXME Why is this dependency present here? Can we exclude it better?
                        component.buildTreePath != ":build-logic:kotlin-dsl-shared-runtime"
                }
            }.files
        })
        destinationDirectory = layout.buildDirectory.dir("public-api/${name}")
    }

    publishing {
        publications {
            create<MavenPublication>(name) {
                groupId = "org.gradle.experimental"
                artifactId = name
                artifact(task)
            }
        }
    }
}

val coreApiTask = registerApiJarTask("core-api", configurations.coreRuntimeClasspath)
val fullApiTask = registerApiJarTask("full-api", configurations.runtimeClasspath)

tasks.register("jarPublicApi") {
    dependsOn(coreApiTask, fullApiTask)
}
