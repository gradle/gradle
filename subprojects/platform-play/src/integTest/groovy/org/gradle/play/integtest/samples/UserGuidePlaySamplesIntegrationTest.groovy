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

package org.gradle.play.integtest.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.JDK7_OR_LATER)
class UserGuidePlaySamplesIntegrationTest extends AbstractIntegrationSpec {
    @Rule Sample sourceSetsPlaySample = new Sample(temporaryFolder, "play/sourcesets")
    @Rule Sample compilerPlaySample = new Sample(temporaryFolder, "play/configure-compiler")
    @Rule Sample distributionPlaySample = new Sample(temporaryFolder, "play/custom-distribution")

    def "sourcesets sample is buildable" () {
        when:
        sample sourceSetsPlaySample

        then:
        succeeds "build"

        and:
        applicationJar(sourceSetsPlaySample).containsDescendants(
            "controllers/hello/HelloController.class",
            "controllers/date/DateController.class",
            "controllers/hello/routes.class",
            "controllers/date/routes.class",
            "html/main.class"
        )
        assetsJar(sourceSetsPlaySample)
    }

    def "compiler sample is buildable" () {
        when:
        sample compilerPlaySample

        then:
        succeeds "build"
    }

    def "distribution sample is buildable" () {
        when:
        sample distributionPlaySample

        then:
        succeeds "build"
    }

    JarTestFixture applicationJar(Sample sample) {
        def projectName = sample.dir.name
        new JarTestFixture(sample.dir.file("build/playBinary/lib/${projectName}.jar"))
    }

    JarTestFixture assetsJar(Sample sample) {
        def projectName = sample.dir.name
        new JarTestFixture(sample.dir.file("build/playBinary/lib/${projectName}-assets.jar"))
    }
}
