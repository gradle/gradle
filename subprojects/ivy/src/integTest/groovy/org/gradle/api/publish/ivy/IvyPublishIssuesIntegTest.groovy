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

package org.gradle.api.publish.ivy;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec;
import org.spockframework.util.TextUtil;
import spock.lang.Issue;

public class IvyPublishIssuesIntegTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2456")
    def "generates SHA1 file with leading zeros"() {
        given:
        def module = ivyRepo.module("org.gradle", "publish", "2")
        byte[] jarBytes = [0, 0, 0, 5]
        def artifactFile = file("testfile.bin")
        artifactFile << jarBytes
        def artifactPath = TextUtil.escape(artifactFile.path)
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'ivy-publish'

            group = "org.gradle"
            version = '2'

            publishing {
                repositories {
                    ivy {
                        url "${ivyRepo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        artifact file: file("${artifactPath}"), name: 'testfile', type: 'bin'
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def shaOneFile = module.moduleDir.file("testfile-2.bin.sha1")
        shaOneFile.exists()
        shaOneFile.text == "00e14c6ef59816760e2c9b5a57157e8ac9de4012"
    }
}
