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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class ResolutionStrategySamplesIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final Sample sample = new Sample(temporaryFolder, 'userguide/artifacts/resolutionStrategy')

    @Requires(TestPrecondition.NOT_JDK5)
    //because some of the api of the samples does not work with JDK5, see GRADLE-1949
    void "can resolve dependencies"()
    {
        mavenRepo.module("org", "foo").publish()
        mavenRepo.module("org", "bar").publish()
        mavenRepo.module("org.gradle", "gradle-core", "1.4").publish()
        mavenRepo.module("org.software", "some-library", "1.2.1").publish()
        mavenRepo.module("org.codehaus", "groovy", "1.7").publish()
        mavenRepo.module("org.slf4j", "log4j-over-slf4j", "1.7.2").publish()

        sample.dir.file("build.gradle") << """
            configurations { conf }
            repositories { maven { url "${mavenRepo.uri}" } }
            dependencies {
                conf "org:foo:1.0"
                conf "org.gradle:gradle-core:1.0"
                conf "org:bar:default"
                conf "org.software:some-library:1.2"
                conf "org.codehaus:groovy-all:1.7"
                conf "log4j:log4j:1.2"
            }
            task resolveConf << { configurations.conf.files }
        """

        when:
        inDirectory(sample.dir)
        //smoke testing if dependency resolution works fine
        run("resolveConf")

        then:
        noExceptionThrown()
    }
}
