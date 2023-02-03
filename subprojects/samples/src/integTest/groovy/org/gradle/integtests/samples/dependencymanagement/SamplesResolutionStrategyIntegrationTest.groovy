/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.samples.dependencymanagement

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

class SamplesResolutionStrategyIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("dependencyManagement/customizingResolution-resolutionStrategy")
    def "can resolve dependencies in #dsl dsl"() {
        TestFile dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()
        mavenRepo.module("org.gradle", "gradle-core", "1.4").publish()
        mavenRepo.module("org.software", "some-library", "1.2.1").publish()
        mavenRepo.module("org.codehaus", "groovy", "2.4.10").publish()
        mavenRepo.module("org.slf4j", "log4j-over-slf4j", "1.7.10").publish()

        dslDir.file(buildScript) << buildScriptUpdate(dsl)

        when:
        inDirectory(dslDir)
        //smoke testing if dependency resolution works fine
        run("resolveConf")

        then:
        noExceptionThrown()

        where:
        dsl      | buildScript
        'groovy' | 'build.gradle'
        'kotlin' | 'build.gradle.kts'
    }

    def buildScriptUpdate(String dsl) {
        if (dsl == 'groovy') {
            return """
                configurations { conf }
                repositories { maven { url "${mavenRepo.uri}" } }
                dependencies {
                    conf "org:foo:1.0"
                    conf "org.gradle:gradle-core:1.4"
                    conf "org:bar:default"
                    conf "org.software:some-library:1.2"
                    conf "org.codehaus:groovy-all:2.4.10"
                    conf "log4j:log4j:1.2"
                }
                task resolveConf {
                    FileCollection conf = configurations.conf
                    doLast { conf.files }
                }
            """
        }
        else if (dsl == 'kotlin') {
            return """
                configurations { create("conf") }
                repositories { maven { url = uri("${mavenRepo.uri}") } }
                dependencies {
                    "conf"("org:foo:1.0")
                    "conf"("org.gradle:gradle-core:1.4")
                    "conf"("org:bar:default")
                    "conf"("org.software:some-library:1.2")
                    "conf"("org.codehaus:groovy-all:2.4.10")
                    "conf"("log4j:log4j:1.2")
                }
                task("resolveConf") {
                    val conf: FileCollection = configurations["conf"]
                    doLast { conf.files }
                }
            """
        }
    }
}
