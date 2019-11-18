/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

class IvyVersionRangePublishIntegrationTest extends AbstractIntegrationSpec {
    def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")

    def setup() {
        // the OLD publish plugins work with the OLD deprecated Java plugin configuration (compile/runtime)
        executer.noDeprecationChecks()
    }

    @ToBeFixedForInstantExecution
    public void "version range is mapped to ivy syntax in published ivy file"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "
        and:
        buildFile << """
apply plugin: 'java'

group = 'org.gradle.test'
version = '1.9'

dependencies {
    compile "group:projectA:latest.release"
    compile "group:projectB:latest.integration"
    compile "group:projectC:1.+"
    compile "group:projectD:[1.0,2.0)"
    compile "group:projectE:[1.0]"
}

uploadArchives {
    repositories {
        ivy {
            url '${ivyRepo.uri}'
        }
    }
}
"""

        when:
        run "uploadArchives"

        then:
        ivyModule.assertPublished()
        ivyModule.parsedIvy.assertDependsOn(
                "group:projectA:latest.release@compile",
                "group:projectB:latest.integration@compile",
                "group:projectC:1.+@compile",
                "group:projectD:[1.0,2.0)@compile",
                "group:projectE:1.0@compile"
        )
    }

    @ToBeFixedForInstantExecution
    def "publishes Ivy dependency version for Gradle dependency with no version"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "
        and:
        buildFile << """
apply plugin: 'java'

group = 'org.gradle.test'
version = '1.9'

dependencies {
    compile "group:projectA"
    compile group:"group", name:"projectB", version:null
}

uploadArchives {
    repositories {
        ivy {
            url '${ivyRepo.uri}'
        }
    }
}
"""

        when:
        run "uploadArchives"

        then:
        ivyModule.parsedIvy.assertDependsOn("group:projectA:@compile", "group:projectB:@compile")
    }
}
