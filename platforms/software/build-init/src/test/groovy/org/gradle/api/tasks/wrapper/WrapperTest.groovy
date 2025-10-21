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

package org.gradle.api.tasks.wrapper

import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.TaskPropertyTestUtils
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.BlockingHttpsServer
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.WrapUtil
import org.gradle.wrapper.GradleWrapperMain
import org.gradle.wrapper.WrapperExecutor
import org.junit.Rule
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class WrapperTest extends AbstractTaskTest {
    private static final String TARGET_WRAPPER_FINAL = "gradle/wrapper"

    @Rule
    BlockingHttpsServer server = new BlockingHttpsServer()
    @Rule
    TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore

    private Wrapper wrapper
    private TestFile expectedTargetWrapperJar
    private File expectedTargetWrapperProperties

    def setup() {
        keyStore = TestKeyStore.init(resources.dir)
        server.configure(keyStore)
        server.start()
        System.setProperty("org.gradle.internal.services.base.url", getBaseUrl())
        keyStore.getTrustStoreSettings().forEach { key, value -> System.setProperty(key, value)}

        wrapper = createTask(Wrapper.class)
        wrapper.setGradleVersion("8.0")
        expectedTargetWrapperJar = new TestFile(getProject().getProjectDir(),
                TARGET_WRAPPER_FINAL + "/gradle-wrapper.jar")
        expectedTargetWrapperProperties = new File(getProject().getProjectDir(),
                TARGET_WRAPPER_FINAL + "/gradle-wrapper.properties")
        new File(getProject().getProjectDir(), TARGET_WRAPPER_FINAL).mkdirs()
        wrapper.setDistributionPath("somepath")
        wrapper.setDistributionSha256Sum("somehash")
    }

    def cleanup() {
        server.stop()
    }

    Wrapper getTask() {
        return wrapper
    }

    def "wrapper has correct defaults"() {
        given:
        wrapper = createTask(Wrapper.class)

        expect:
        new File(getProject().getProjectDir(), TARGET_WRAPPER_FINAL + "/gradle-wrapper.jar") == wrapper.getJarFile()
        new File(getProject().getProjectDir(), "gradlew") == wrapper.getScriptFile()
        new File(getProject().getProjectDir(), "gradlew.bat") == wrapper.getBatchScript()
        GradleVersion.current().getVersion() == wrapper.getGradleVersion()
        Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME == wrapper.getDistributionPath()
        Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME == wrapper.getArchivePath()
        Wrapper.PathBase.GRADLE_USER_HOME == wrapper.getDistributionBase()
        Wrapper.PathBase.GRADLE_USER_HOME == wrapper.getArchiveBase()
        wrapper.getDistributionUrl() != null
        wrapper.getDistributionSha256Sum() == null
        !wrapper.getNetworkTimeout().isPresent()
        wrapper.getValidateDistributionUrl()
    }

    def "determines Windows script path from unix script path with #inName"() {
        when:
        wrapper.setScriptFile("build/$inName")

        then:
        getProject().file("build/$outName") == wrapper.getBatchScript()

        where:
        inName           | outName
        "gradle.sh"      | "gradle.bat"
        "gradle-wrapper" | "gradle-wrapper.bat"
    }

    def "determines properties file path from jar path"() {
        given:
        wrapper.setJarFile("build/gradle-wrapper.jar")

        expect:
        getProject().file("build/gradle-wrapper.properties") == wrapper.getPropertiesFile()
    }

    def "downloads for '#version' from repository "() {
        given:
        if (request != null) {
            server.expect(server.get(request).send(reply))
        }

        when:
        wrapper.setGradleVersion(version)

        then:
        wrapper.getDistributionUrl() == getBaseUrl() + downloadUrlSuffix

        where:
        version                     | request                       | reply                                              | downloadUrlSuffix
        "8.13"                      | null                          | null                                               | "/distributions/gradle-8.13-bin.zip"
        "7.6-milestone-1"           | null                          | null                                               | "/distributions/gradle-7.6-milestone-1-bin.zip"
        "9.9.1-20101224110000+1100" | null                          | null                                               | "/distributions-snapshots/gradle-9.9.1-20101224110000+1100-bin.zip"
        "latest"                    | "/versions/current"           | """{ "version" : "8.14.1" }"""                     | "/distributions/gradle-8.14.1-bin.zip"
        "release-candidate"         | "/versions/release-candidate" | """{ "version" : "9.0-RC-1"}"""                    | "/distributions/gradle-9.0-RC-1-bin.zip"
        "release-milestone"         | "/versions/milestone"         | """{ "version" : "9.0.0-milestone-9" }"""          | "/distributions/gradle-9.0.0-milestone-9-bin.zip"
        "release-nightly"           | "/versions/release-nightly"   | """{ "version" : "8.14.1-20250522010941+0000" }""" | "/distributions-snapshots/gradle-8.14.1-20250522010941+0000-bin.zip"
        "nightly"                   | "/versions/nightly"           | """{ "version" : "9.0.0-20250603003140+0000" }"""  | "/distributions-snapshots/gradle-9.0.0-20250603003140+0000-bin.zip"
    }

    def "uses explicitly defined distribution url"() {
        given:
        wrapper.setGradleVersion("0.9")
        wrapper.setDistributionUrl("http://some-url")

        expect:
        "http://some-url" == wrapper.getDistributionUrl()
    }

    def "uses explicitly defined distribution sha256 sum"() {
        given:
        wrapper.setDistributionSha256Sum("somehash")

        expect:
        "somehash" == wrapper.getDistributionSha256Sum()
    }

    def "uses defined network timeout"() {
        given:
        wrapper.setNetworkTimeout(5000)

        expect:
        5000 == wrapper.getNetworkTimeout().get()
    }

    def "uses defined validateDistributionUrl value"() {
        when:
        wrapper.setValidateDistributionUrl(false)

        then:
        !wrapper.getValidateDistributionUrl().get()
    }

    def "execute with non-existent wrapper jar parent directory"() {
        given:
        server.expect(server.head("/distributions/gradle-8.0-bin.zip"))

        when:
        def decompressDir = temporaryFolder.createDir("decompress")
        execute(wrapper)
        expectedTargetWrapperJar.unzipToWithoutCheckingParentDirs(decompressDir)
        def properties = GUtil.loadProperties(expectedTargetWrapperProperties)

        then:
        decompressDir.file(GradleWrapperMain.class.getName().replace(".", "/") + ".class").assertIsFile()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY) == wrapper.getDistributionUrl()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_SHA_256_SUM) == wrapper.getDistributionSha256Sum()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY) == wrapper.getDistributionBase().toString()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY) == wrapper.getDistributionPath()
        properties.getProperty(WrapperExecutor.ZIP_STORE_BASE_PROPERTY) == wrapper.getArchiveBase().toString()
        properties.getProperty(WrapperExecutor.ZIP_STORE_PATH_PROPERTY) == wrapper.getArchivePath()
    }

    def "execute with networkTimeout set"() {
        given:
        server.expect(server.head("/distributions/gradle-8.0-bin.zip"))
        wrapper.setNetworkTimeout(6000)

        when:
        execute(wrapper)
        def properties = GUtil.loadProperties(expectedTargetWrapperProperties)

        then:
        properties.getProperty(WrapperExecutor.NETWORK_TIMEOUT_PROPERTY) == "6000"
    }

    def "execute with validateDistributionUrl set"() {
        given:
        wrapper.setValidateDistributionUrl(false)

        when:
        execute(wrapper)
        def properties = GUtil.loadProperties(expectedTargetWrapperProperties)

        then:
        properties.getProperty(WrapperExecutor.VALIDATE_DISTRIBUTION_URL) == "false"
    }

    def "check inputs"() {
        expect:
        TaskPropertyTestUtils.getProperties(wrapper).keySet() == WrapUtil.toSet(
            "distributionBase", "distributionPath", "distributionUrl", "distributionSha256Sum",
            "distributionType", "archiveBase", "archivePath", "gradleVersion", "networkTimeout", "validateDistributionUrl")
    }

    def "execute with extant wrapper jar parent directory and extant wrapper jar"() {
        given:
        server.expect(server.head("/distributions/gradle-8.0-bin.zip"))

        def jarDir = new File(getProject().getProjectDir(), "lib")
        jarDir.mkdirs()
        def parentFile = expectedTargetWrapperJar.getParentFile()

        expect:
        parentFile.isDirectory() || parentFile.mkdirs()
        expectedTargetWrapperJar.createNewFile()

        when:
        def unjarDir = temporaryFolder.createDir("unjar")
        execute(wrapper)
        expectedTargetWrapperJar.unzipToWithoutCheckingParentDirs(unjarDir)
        def properties = GUtil.loadProperties(expectedTargetWrapperProperties)

        then:
        unjarDir.file(GradleWrapperMain.class.getName().replace(".", "/") + ".class").assertIsFile()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY) == wrapper.getDistributionUrl()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_SHA_256_SUM) == wrapper.getDistributionSha256Sum()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY) == wrapper.getDistributionBase().toString()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY) == wrapper.getDistributionPath()
        properties.getProperty(WrapperExecutor.ZIP_STORE_BASE_PROPERTY) == wrapper.getArchiveBase().toString()
        properties.getProperty(WrapperExecutor.ZIP_STORE_PATH_PROPERTY) == wrapper.getArchivePath()
    }

    def "distributionUrl should not contain small dotless I letter when locale has small dotless I letter"() {
        given:
        server.expect(server.head("/distributions/gradle-8.0-bin.zip"))

        Locale originalLocale = Locale.getDefault()
        Locale.setDefault(new Locale("tr","TR"))

        when:
        execute(wrapper)
        String distributionUrl = wrapper.getDistributionUrl()

        then:
        !distributionUrl.contains("\u0131")

        cleanup:
        Locale.setDefault(originalLocale)
    }

    def "result of Wrapper.getAvailableDistributionTypes() is equal to Wrapper.DistributionType.values()"() {
        expect:
        wrapper.availableDistributionTypes == Wrapper.DistributionType.values() as List
    }

    private String getBaseUrl() {
        server.uri.toString()
    }
}
