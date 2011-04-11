/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.apache.tools.ant.taskdefs.Expand
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.AntUtil
import org.gradle.util.GradleVersion
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class DistributionIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    private String version = GradleVersion.current().version

    @Test
    public void binZipContents() {
        TestFile binZip = dist.distributionsDir.file("gradle-$version-bin.zip")
        binZip.usingNativeTools().unzipTo(dist.testDir)
        TestFile contentsDir = dist.testDir.file("gradle-$version")

        checkMinimalContents(contentsDir)

        // Extra stuff
        contentsDir.file('src').assertDoesNotExist()
        contentsDir.file('samples').assertDoesNotExist()
        contentsDir.file('docs').assertDoesNotExist()
    }

    @Test
    public void allZipContents() {
        TestFile binZip = dist.distributionsDir.file("gradle-$version-all.zip")
        binZip.usingNativeTools().unzipTo(dist.testDir)
        TestFile contentsDir = dist.testDir.file("gradle-$version")

        checkMinimalContents(contentsDir)

        // Source
        contentsDir.file('src/org/gradle/api/Project.java').assertIsFile()
        contentsDir.file('src/org/gradle/initialization/defaultBuildSourceScript.txt').assertIsFile()
        contentsDir.file('src/org/gradle/gradleplugin/userinterface/swing/standalone/BlockingApplication.java').assertIsFile()
        contentsDir.file('src/org/gradle/wrapper/Wrapper.java').assertIsFile()

        // Samples
        contentsDir.file('samples/java/quickstart/build.gradle').assertIsFile()

        // Javadoc
        contentsDir.file('docs/javadoc/index.html').assertIsFile()
        contentsDir.file('docs/javadoc/index.html').assertContents(containsString("<title>Overview (Gradle API ${version})</title>"))
        contentsDir.file('docs/javadoc/org/gradle/api/Project.html').assertIsFile()

        // Groovydoc
        contentsDir.file('docs/groovydoc/index.html').assertIsFile()
        contentsDir.file('docs/groovydoc/org/gradle/api/Project.html').assertIsFile()
        contentsDir.file('docs/groovydoc/org/gradle/api/tasks/bundling/Zip.html').assertIsFile()

        // Userguide
        contentsDir.file('docs/userguide/userguide.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide.html').assertContents(containsString("<h3 class=\"releaseinfo\">Version ${version}</h3>"))
        contentsDir.file('docs/userguide/userguide_single.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide_single.html').assertContents(containsString("<h3 class=\"releaseinfo\">Version ${version}</h3>"))
//        contentsDir.file('docs/userguide/userguide.pdf').assertIsFile()

        // DSL reference
        contentsDir.file('docs/dsl/index.html').assertIsFile()
        contentsDir.file('docs/dsl/index.html').assertContents(containsString("<title>Gradle DSL Version ${version}</title>"))
    }

    private def checkMinimalContents(TestFile contentsDir) {
        // Check it can be executed
        executer.inDirectory(contentsDir).usingExecutable('bin/gradle').withTaskList().run()

        // Scripts
        contentsDir.file('bin/gradle').assertIsFile()
        contentsDir.file('bin/gradle.bat').assertIsFile()

        // Top level files
        contentsDir.file('LICENSE').assertIsFile()

        // Libs
        assertIsGradleJar(contentsDir.file("lib/gradle-core-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-ui-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-launcher-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-tooling-api-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-wrapper-${version}.jar"))

        // TODO - these should be in lib/plugins
        assertIsGradleJar(contentsDir.file("lib/gradle-plugins-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-ide-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-scala-${version}.jar"))

        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-code-quality-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-antlr-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-announce-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-jetty-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-sonar-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-maven-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-osgi-${version}.jar"))

        // Docs
        contentsDir.file('getting-started.html').assertIsFile()
    }

    private def assertIsGradleJar(TestFile jar) {
        jar.assertIsFile()
        assertThat(jar.manifest.mainAttributes.getValue('Implementation-Version'), equalTo(version))
        assertThat(jar.manifest.mainAttributes.getValue('Implementation-Title'), equalTo('Gradle'))
    }

    @Test
    public void sourceZipContents() {
        TestFile srcZip = dist.distributionsDir.file("gradle-$version-src.zip")
        srcZip.usingNativeTools().unzipTo(dist.testDir)
        TestFile contentsDir = dist.testDir.file("gradle-$version")

        // Build self using wrapper in source distribution
        executer.inDirectory(contentsDir).usingExecutable('gradlew').withTasks('binZip').run()

        File binZip = contentsDir.file('build/distributions').listFiles()[0]
        Expand unpack = new Expand()
        unpack.src = binZip
        unpack.dest = contentsDir.file('build/distributions/unzip')
        AntUtil.execute(unpack)
        TestFile unpackedRoot = new TestFile(contentsDir.file('build/distributions/unzip').listFiles()[0])

        // Make sure the build distribution does something useful
        unpackedRoot.file("bin/gradle").assertIsFile()
        // todo run something with the gradle build by the source dist
    }
}
