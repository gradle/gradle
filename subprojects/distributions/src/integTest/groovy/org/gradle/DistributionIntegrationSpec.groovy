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

    abstract int getLibJarsCount()

    def "no duplicate entries"() {
        given:
        def entriesByPath = zipEntries.findAll { !it.name.contains('/META-INF/services/') }.groupBy { it.name }
        def dupes = entriesByPath.findAll { it.value.size() > 1 }

        when:
        def dupesWithCount = dupes.collectEntries { [it.key, it.value.size()]}

        then:
        dupesWithCount.isEmpty()
    }

    def "all files under lib directory are jars"() {
        when:
        def nonJarLibEntries = libZipEntries.findAll { !it.name.endsWith(".jar") }

        then:
        nonJarLibEntries.isEmpty()
    }

    def "no additional jars are added to the distribution"() {
        when:
        def jarLibEntries = libZipEntries.findAll { it.name.endsWith(".jar") }

        then:
        //ME: This is not a foolproof way of checking that additional jars have not been accidentally added to the distribution
        //but should be good enough. If this test fails for you and you did not intend to add new jars to the distribution
        //then there is something to be fixed. If you intentionally added new jars to the distribution and this is now failing please
        //accept my sincere apologies that you have to manually bump the numbers here.
        jarLibEntries.size() == libJarsCount
    }

    protected List<? extends ZipEntry> getLibZipEntries() {
        zipEntries.findAll { !it.isDirectory() && it.name.tokenize("/")[1] == "lib" }
    }

    protected List<? extends ZipEntry> getZipEntries() {
        ZipFile zipFile = new ZipFile(zip)
        try {
            zipFile.entries().toList()
        } finally {
            zipFile.close()
        }
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
        assert coreLibs.size() == 15
        coreLibs.each { assertIsGradleJar(it) }

        def toolingApiJar = contentsDir.file("lib/gradle-tooling-api-${version}.jar")
        toolingApiJar.assertIsFile()
        assert toolingApiJar.length() < 300 * 1024; // tooling api jar is the small plain tooling api jar version and not the fat jar.

        // Plugins
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-dependency-management-${version}.jar"))
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
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ear-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-platform-native-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ide-native-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-native-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-platform-jvm-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-jvm-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-java-${version}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-groovy-${version}.jar"))

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
