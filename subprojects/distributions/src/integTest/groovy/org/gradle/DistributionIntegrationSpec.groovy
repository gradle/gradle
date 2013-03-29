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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.PreconditionVerifier
import org.junit.Rule
import spock.lang.Shared

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertThat

abstract class DistributionIntegrationSpec extends AbstractIntegrationSpec {

    @Rule public final PreconditionVerifier preconditionVerifier = new PreconditionVerifier()

    @Shared String version = GradleVersion.current().version

    abstract String getDistributionLabel()

    def "no duplicate entries"() {
        given:
        ZipFile zipFile = new ZipFile(zip)

        when:
        def entries = zipFile.entries().toList()
        def entriesByPath = entries.groupBy { ZipEntry zipEntry -> zipEntry.name }
        def dupes = entriesByPath.findAll { it.value.size() > 1 && !it.key.contains('/META-INF/services/') }
        def dupesWithCount = dupes.collectEntries { [it.key, it.value.size()]}

        then:
        dupesWithCount.isEmpty()
    }

    protected TestFile unpackDistribution(type = getDistributionLabel()) {
        TestFile zip = getZip(type)
        zip.usingNativeTools().unzipTo(testDirectory)
        TestFile contentsDir = file("gradle-$version")
        contentsDir
    }

    protected TestFile getZip(String type = getDistributionLabel()) {
        new IntegrationTestBuildContext().distributionsDir.file("gradle-$version-${type}.zip")
    }

    protected void checkMinimalContents(TestFile contentsDir) {
        // Check it can be executed
        executer.inDirectory(contentsDir).usingExecutable('bin/gradle').withTaskList().run()

        // Scripts
        contentsDir.file('bin/gradle').assertIsFile()
        contentsDir.file('bin/gradle.bat').assertIsFile()

        // Top level files
        contentsDir.file('LICENSE').assertIsFile()

        // Core libs
        def coreLibs = contentsDir.file("lib").listFiles().findAll { it.name.startsWith("gradle-") }
        assert coreLibs.size() == 11
        coreLibs.each { assertIsGradleJar(it) }

        def toolingApiJar = contentsDir.file("lib/gradle-tooling-api-${version}.jar")
        toolingApiJar.assertIsFile()
        assert toolingApiJar.length() < 200 * 1024; // tooling api jar is the small plain tooling api jar version and not the fat jar.

        // Plugins
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-core-impl-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-plugins-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ide-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-scala-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-code-quality-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-antlr-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-announce-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-jetty-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-sonar-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-maven-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-osgi-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-signing-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-cpp-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ear-${version}.jar"))

        // Docs
        contentsDir.file('getting-started.html').assertIsFile()

        // Jars that must not be shipped
        assert !contentsDir.file("lib/tools.jar").exists()
        assert !contentsDir.file("lib/plugins/tools.jar").exists()
    }

    protected void assertIsGradleJar(TestFile jar) {
        jar.assertIsFile()
        assertThat(jar.manifest.mainAttributes.getValue('Implementation-Version'), equalTo(version))
        assertThat(jar.manifest.mainAttributes.getValue('Implementation-Title'), equalTo('Gradle'))
    }
}
