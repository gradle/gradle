/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class CompositeBuildDependencyCapabilitiesResolveIntegrationTest extends AbstractIntegrationSpec {

    def "dependency capabilities travel to the included build"() {
        mavenRepo.module('com.acme.external', 'external', '1.0')

        given:
        settingsFile << """
            rootProject.name = 'test'
            includeBuild "includedBuild"
        """
        file('includedBuild/settings.gradle') << '''
            rootProject.name = 'external'
        '''
        file("includedBuild/build.gradle") << """
            group = 'com.acme.external'
            version = '2.0-SNAPSHOT'

            configurations {
                first {
                   attributes {
                       attribute(Attribute.of('org.gradle.usage', Usage), project.objects.named(Usage, 'java-api'))
                   }
                   outgoing.capability('org:cap1:1.0')
                }
                second {
                   attributes {
                       attribute(Attribute.of('org.gradle.usage', Usage), project.objects.named(Usage, 'java-api'))
                   }
                   outgoing.capability('org:cap2:1.0')
                }
            }

            artifacts {
                first file("first-\${version}.jar")
                second file("second-\${version}.jar")
            }
        """

        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                api("com.acme.external:external:1.0") {
                    capabilities {
                        requireCapability("org:$capability")
                    }
                }
            }
        """
        def resolve = new ResolveTestFixture(buildFile, "compileClasspath")
        resolve.prepare()

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.acme.external:external:1.0", ":includedBuild", "com.acme.external:external:2.0-SNAPSHOT") {
                    compositeSubstitute()
                    variant(expectedVariant, ['org.gradle.usage': 'java-api'])
                    artifact(name: expectedVariant)
                }
            }
        }

        where:
        capability | expectedVariant
        'cap1'     | 'first'
        'cap2'     | 'second'
    }
}
