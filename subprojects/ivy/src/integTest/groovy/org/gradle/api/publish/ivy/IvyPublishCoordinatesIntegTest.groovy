/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.api.publish.ivy

public class IvyPublishCoordinatesIntegTest extends AbstractIvyPublishIntegTest {

    def "can publish simple jar with specified coordinates"() {
        given:
        def module = ivyRepo.module('org.custom', 'custom', '2.2')

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        organisation "org.custom"
                        module "custom"
                        revision "2.2"
                    }
                }
            }
        """

        when:
        succeeds 'assemble'

        then: "jar is built but not published"
        module.assertNotPublished()
        file('build/libs/root-1.0.jar').assertExists()

        when:
        succeeds 'publish'

        then: "jar is published to defined ivy repository"
        module.assertPublishedAsJavaModule()
        module.ivy.status == 'integration'
        module.moduleDir.file('custom-2.2.jar').assertIsCopyOf(file('build/libs/root-1.0.jar'))

        and:
        resolveArtifacts(module) == ['custom-2.2.jar']
    }

}
