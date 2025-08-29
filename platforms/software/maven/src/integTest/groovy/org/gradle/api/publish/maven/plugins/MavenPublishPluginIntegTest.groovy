/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest

class MavenPublishPluginIntegTest extends WellBehavedPluginTest {

    @Override
    String getMainTask() {
        "publish"
    }

    def "can publish configurations without realizing them eagerly"() {
        given:
        settingsKotlinFile << """
            rootProject.name = "test"
        """
        buildKotlinFile << """
            plugins {
                id("base")
                id("maven-publish")
            }

            group = "org.example"
            version = "1.0"

            val myTask = tasks.register<Jar>("myTask")
            val variantDependencies = configurations.dependencyScope("variantDependencies")
            val myNewVariant: NamedDomainObjectProvider<ConsumableConfiguration> = configurations.consumable("myNewVariant") {
                extendsFrom(variantDependencies.get())
                outgoing {
                    artifact(myTask)
                }
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named<Category>("foo"))
                }
            }


            publishing {
                val component = softwareComponentFactory.adhoc("component")
                // This new overload now accepts a lazy provider of consumable configuration
                component.addVariantsFromConfiguration(myNewVariant) {}

                repositories {
                    maven {
                        url = uri("${mavenRepo.uri}")
                    }
                }
                publications {
                    create<MavenPublication>("myPublication") {
                        from(component)
                    }
                }
            }
        """

        when:
        succeeds("publish")

        then:
        mavenRepo.rootDir.file("org/example/test/1.0/test-1.0.pom").assertExists()
        mavenRepo.rootDir.file("org/example/test/1.0/test-1.0.module").assertExists()
        mavenRepo.rootDir.file("org/example/test/1.0/test-1.0.jar").assertExists()
    }

}
