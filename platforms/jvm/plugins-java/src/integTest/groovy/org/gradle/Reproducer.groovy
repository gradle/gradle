/*
 * Copyright 2023 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class Reproducer extends AbstractIntegrationSpec {
    def "test"() {
        given:
        file("buildSrc/build.gradle") << """
            plugins {
                id("groovy-gradle-plugin")
            }
        """
        file("buildSrc/src/main/groovy/MyExtension.java") << """
            import org.gradle.api.plugins.JavaPluginExtension;
            abstract class MyExtension {

                private final JavaPluginExtension ext;

                @javax.inject.Inject
                public MyExtension(JavaPluginExtension ext) {
                    this.ext = ext;
                }

                void doSomethingDeprecated() {
                    ext.withSourcesJar();
                }
            }
        """
        file("buildSrc/src/main/groovy/my.plugin.gradle") << """
            plugins {
                id 'java-base'
            }

            sourceSets {
                main
            }
            tasks.create("javadoc", Javadoc)

            // 1
            java {
                withJavadocJar()
            }

            extensions.add(MyExtension, "myExtension", objects.newInstance(MyExtension, extensions.getByType(JavaPluginExtension)))
        """
        buildFile << """
            plugins {
                id 'my.plugin'
            }

            // 2
            java {
                consistentResolution {}
            }

            // 3
            myExtension.doSomethingDeprecated()

            // Attribute nothing if no plugin & no stack trace
                // Be smarter about stack trace
        """

        expect:
        succeeds "help"
    }
}
