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
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.archive.ArchiveTestFixture
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.IgnoreIf

@Requires(TestPrecondition.JDK8_OR_LATER)
class UserGuidePlaySamplesIntegrationTest extends AbstractIntegrationSpec {
    @Rule Sample sourceSetsPlaySample = new Sample(temporaryFolder, "play/sourcesets")
    @Rule Sample compilerPlaySample = new Sample(temporaryFolder, "play/configure-compiler")
    @Rule Sample distributionPlaySample = new Sample(temporaryFolder, "play/custom-distribution")
    @Rule Sample customAssetsPlaySample = new Sample(temporaryFolder, "play/custom-assets")
    @Rule Sample play24Sample = new Sample(temporaryFolder, "play/play-2.4")
    @Rule Sample play26Sample = new Sample(temporaryFolder, "play/play-2.6")

    def setup() {
        executer.usingInitScript(RepoScriptBlockUtil.createMirrorInitScript())
    }

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
        assetsJar(sourceSetsPlaySample).with {
            containsDescendants(
                "public/sample.js"
            )
            doesNotContainDescendants(
                "public/old_sample.js"
            )
        }
    }

    @IgnoreIf({ !AbstractPlaySampleIntegrationTest.portForWithBrowserTestIsFree() })
    def "compiler sample is buildable" () {
        when:
        // The following annotation processors were detected on the compile classpath: 'org.atteo.classindex.processor.ClassIndexProcessor'.
        // Detecting annotation processors on the compile classpath is deprecated
        executer.expectDeprecationWarning()
        sample compilerPlaySample

        then:
        succeeds "build"

        and:
        applicationJar(compilerPlaySample).containsDescendants(
            "controllers/Application.class"
        )
    }

    def "distribution sample is buildable" () {
        when:
        sample distributionPlaySample

        then:
        succeeds "dist"

        and:
        distributionArchives(distributionPlaySample)*.containsDescendants(
            "playBinary/README.md",
            "playBinary/bin/runPlayBinaryAsUser.sh"
        )
    }

    def "custom assets sample is buildable" () {
        when:
        sample customAssetsPlaySample

        then:
        succeeds "build"

        and:
        customAssetsPlaySample.dir.file("build/playBinary/addCopyRights/sample.js").text.contains("* Copyright 2015")
        assetsJar(customAssetsPlaySample).containsDescendants(
            "public/sample.js"
        )
    }

    @Requires(TestPrecondition.JDK8)
    def "injected routes sample is buildable for Play 2.4" () {
        when:
        sample play24Sample

        then:
        succeeds "build"

        and:
        play24Sample.dir.file("build/src/play/binary/routesScalaSources").assertHasDescendants(
            "controllers/routes.java",
            "controllers/ReverseRoutes.scala",
            "router/Routes.scala",
            "controllers/javascript/JavaScriptReverseRoutes.scala",
            "router/RoutesPrefix.scala")
    }

    def "injected routes sample is buildable for Play 2.6" () {
        when:
        sample play26Sample

        then:
        succeeds "build"

        and:
        play26Sample.dir.file("build/src/play/binary/routesScalaSources").assertHasDescendants(
            "controllers/routes.java",
            "controllers/ReverseRoutes.scala",
            "router/Routes.scala",
            "controllers/javascript/JavaScriptReverseRoutes.scala",
            "router/RoutesPrefix.scala")
    }

    JarTestFixture applicationJar(Sample sample) {
        def projectName = sample.dir.name
        new JarTestFixture(sample.dir.file("build/playBinary/lib/${projectName}.jar"))
    }

    JarTestFixture assetsJar(Sample sample) {
        def projectName = sample.dir.name
        new JarTestFixture(sample.dir.file("build/playBinary/lib/${projectName}-assets.jar"))
    }

    List<ArchiveTestFixture> distributionArchives(Sample sample) {
        [ new ZipTestFixture(sample.dir.file("build/distributions/playBinary.zip")), new TarTestFixture(sample.dir.file("build/distributions/playBinary.tar")) ]
    }
}
