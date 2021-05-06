/*
 * Copyright 2011 the original author or authors.
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


import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class IvyJavaProjectPublishIntegrationTest extends AbstractLegacyIvyPublishTest {

    def setup() {
        // the OLD publish plugins work with the OLD deprecated Java plugin configuration (compile/runtime)
        executer.noDeprecationChecks()
    }

    @ToBeFixedForConfigurationCache
    void "can publish jar and meta-data to ivy repository"() {
        given:
        file("settings.gradle") << "rootProject.name = 'publishTest' "

        and:
        buildFile << """
apply plugin: 'java'

group = 'org.gradle.test'
version = '1.9'

${mavenCentralRepository()}

configurations.implementation {
    exclude group: "foo", module: "bar"
}

dependencies {
    implementation "commons-collections:commons-collections:3.2.2"
    compileOnly "javax.servlet:servlet-api:2.5"
    runtimeOnly "commons-io:commons-io:1.4"
    implementation("commons-beanutils:commons-beanutils:1.8.3") {
        exclude group: 'commons-logging'
    }
    constraints {
        implementation "org.apache.commons:commons-math3:3.0"
    }
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
        expectUploadTaskDeprecationWarning("uploadArchives")
        run "uploadArchives"

        then:
        def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")

        ivyModule.assertArtifactsPublished("ivy-1.9.xml", "publishTest-1.9.jar")

        ivyModule.parsedIvy.dependencies.size() == 5
        ivyModule.parsedIvy.dependencies["commons-collections:commons-collections:3.2.2"].hasConf("implementation->default")
        ivyModule.parsedIvy.dependencies["commons-io:commons-io:1.4"].hasConf("runtimeOnly->default")
        ivyModule.parsedIvy.dependencies["javax.servlet:servlet-api:2.5"].hasConf("compileOnly->default")
        ivyModule.parsedIvy.dependencies["commons-beanutils:commons-beanutils:1.8.3"].exclusions[0].org == 'commons-logging'
        // Not ideal, but documents the way this works for now
        ivyModule.parsedIvy.dependencies["org.apache.commons:commons-math3:3.0"].hasConf("implementation->default")

        ivyModule.parsedIvy.exclusions.collect { it.org + ":" + it.module + "@" + it.conf} == ["foo:bar@implementation"]
    }
}
