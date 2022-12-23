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

import org.apache.commons.io.IOUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GUtil
import spock.lang.Shared

import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

abstract class DistributionIntegrationSpec extends AbstractIntegrationSpec {

    protected static final THIRD_PARTY_LIB_COUNT = 151

    @Shared String baseVersion = GradleVersion.current().baseVersion.version

    abstract String getDistributionLabel()

    abstract int getMaxDistributionSizeBytes()

    /**
     * Change this whenever you add or remove subprojects for distribution core modules (lib/).
     */
    int getCoreLibJarsCount() {
        44
    }

    /**
     * Change this whenever you add or remove subprojects for distribution-packaged plugins (lib/plugins).
     */
    int getPackagedPluginsJarCount() {
        46
    }

    /**
     * Change this whenever you add or remove subprojects for distribution java agents (lib/agents).
     * @return
     */
    int getAgentJarsCount() {
        1
    }

    /**
     * Change this if you added or removed dependencies.
     */
    int getThirdPartyLibJarsCount() {
        THIRD_PARTY_LIB_COUNT
    }

    int getLibJarsCount() {
        coreLibJarsCount + packagedPluginsJarCount + agentJarsCount + thirdPartyLibJarsCount
    }

    def "distribution size should not exceed a certain number"() {
        expect:
        def size = getZip().size()

        println("######## Distribution size ${size}")
        size <= getMaxDistributionSizeBytes()
    }

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

    protected TestFile unpackDistribution(type = getDistributionLabel(), TestFile into = testDirectory) {
        TestFile zip = getZip(type)
        zip.usingNativeTools().unzipTo(into)
        assert into.listFiles().size() == 1
        into.listFiles()[0]
    }

    protected TestFile getZip(String type = getDistributionLabel()) {
        switch (type) {
            case 'bin':
                buildContext.binDistribution
                break
            case 'all':
                buildContext.allDistribution
                break
            case 'docs':
                buildContext.docsDistribution
                break
            case 'src':
                buildContext.srcDistribution
                break
            default:
                throw new RuntimeException("Unknown distribution type '$type'")
        }
    }

    protected void checkMinimalContents(TestFile contentsDir) {
        // Check it can be executed
        executer.inDirectory(contentsDir).usingExecutable('bin/gradle').withTasks("help").run()

        // Scripts
        contentsDir.file('bin/gradle').assertIsFile()
        contentsDir.file('bin/gradle.bat').assertIsFile()

        // Top level files
        contentsDir.file('LICENSE').assertIsFile()

        // Core libs
        def coreLibs = contentsDir.file("lib").listFiles().findAll {
            it.name.startsWith("gradle-") && !it.name.startsWith("gradle-api-metadata") && !it.name.startsWith("gradle-kotlin-dsl")
        }
        assert coreLibs.size() == coreLibJarsCount
        coreLibs.each { assertIsGradleJar(it) }

        def toolingApiJar = contentsDir.file("lib/gradle-tooling-api-${baseVersion}.jar")
        toolingApiJar.assertIsFile()
        assert toolingApiJar.length() < 500 * 1024 // tooling api jar is the small plain tooling api jar version and not the fat jar.

        // Plugins
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-dependency-management-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-version-control-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-plugins-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ide-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-scala-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-code-quality-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-antlr-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-maven-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-signing-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ear-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-platform-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ide-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-platform-jvm-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-jvm-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-java-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-groovy-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-enterprise-${baseVersion}.jar"))

        // Agents
        assertIsGradleJar(contentsDir.file("lib/agents/gradle-instrumentation-agent-${baseVersion}.jar"))

        // Docs
        contentsDir.file('README').assertIsFile()

        // Others
        assertIsGradleApiMetadataJar(contentsDir.file("lib/gradle-api-metadata-${baseVersion}.jar"))

        // Jars that must not be shipped
        assert !contentsDir.file("lib/tools.jar").exists()
        assert !contentsDir.file("lib/plugins/tools.jar").exists()
    }

    protected static void assertDocsExist(TestFile contentsDir, String version) {
        // Javadoc
        contentsDir.file('docs/javadoc/index.html').assertIsFile()
        contentsDir.file('docs/javadoc/index.html').assertContents(containsString("Gradle API ${version}"))
        contentsDir.file('docs/javadoc/org/gradle/api/Project.html').assertIsFile()

        // Userguide
        contentsDir.file('docs/userguide/userguide.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide.html').assertContents(containsString("Gradle User Manual</h1>"))
        contentsDir.file('docs/userguide/userguide_single.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide_single.html').assertContents(containsString("<h1>Gradle User Manual: Version ${version}</h1>"))
        contentsDir.file('docs/userguide/userguide.pdf').assertIsFile()

        // DSL reference
        contentsDir.file('docs/dsl/index.html').assertIsFile()
        contentsDir.file('docs/dsl/index.html').assertContents(containsString("<title>Gradle DSL Version ${version}</title>"))
    }

    protected void assertIsGradleJar(TestFile jar) {
        jar.assertIsFile()
        assertThat(jar.name, jar.manifest.mainAttributes.getValue('Implementation-Version'), equalTo(baseVersion))
        assertThat(jar.name, jar.manifest.mainAttributes.getValue('Implementation-Title'), equalTo('Gradle'))
    }

    private static void assertIsGradleApiMetadataJar(TestFile jar) {
        new JarTestFixture(jar.canonicalFile).with {
            def apiDeclaration = GUtil.loadProperties(IOUtils.toInputStream(content("gradle-api-declaration.properties"), StandardCharsets.UTF_8))
            assert apiDeclaration.size() == 2
            assert apiDeclaration.getProperty("includes").contains(":org/gradle/api/**:")
            assert apiDeclaration.getProperty("excludes").split(":").size() == 1
        }
    }
}
