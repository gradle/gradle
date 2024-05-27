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
import org.gradle.api.tasks.wrapper.internal.DefaultWrapperVersionsResources
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.WrapUtil
import org.gradle.wrapper.GradleWrapperMain
import org.gradle.wrapper.WrapperExecutor

class WrapperTest extends AbstractTaskTest {
    private static final String RELEASE = "7.6"
    private static final String RELEASE_CANDIDATE = "7.7-rc-5"
    private static final String NIGHTLY = "8.1-20221207164726+0000"
    private static final String RELEASE_NIGHTLY = "8.0-20221207164726+0000"
    private static final String TARGET_WRAPPER_FINAL = "gradle/wrapper"
    private Wrapper wrapper
    private TestFile expectedTargetWrapperJar
    private File expectedTargetWrapperProperties

    def createVersionTextResource(String version) {
        wrapper.getProject().getResources().text.fromString("""{ "version" : "${version}" }""")
    }

    def setup() {
        wrapper = createTask(Wrapper.class)

        def latest = createVersionTextResource(RELEASE)
        def releaseCandidate = createVersionTextResource(RELEASE_CANDIDATE)
        def nightly = createVersionTextResource(NIGHTLY)
        def releaseNightly = createVersionTextResource(RELEASE_NIGHTLY)
        wrapper.setWrapperVersionsResources(new DefaultWrapperVersionsResources(latest, releaseCandidate, nightly, releaseNightly))
        wrapper.getGradleVersion().set("1.0")
        expectedTargetWrapperJar = new TestFile(getProject().getProjectDir(),
                TARGET_WRAPPER_FINAL + "/gradle-wrapper.jar")
        expectedTargetWrapperProperties = new File(getProject().getProjectDir(),
                TARGET_WRAPPER_FINAL + "/gradle-wrapper.properties")
        new File(getProject().getProjectDir(), TARGET_WRAPPER_FINAL).mkdirs()
        wrapper.getDistributionPath().set("somepath")
        wrapper.getDistributionSha256Sum().set("somehash")
    }

    Wrapper getTask() {
        return wrapper
    }

    def "wrapper has correct defaults"() {
        given:
        wrapper = createTask(Wrapper.class)

        expect:
        new File(getProject().getProjectDir(), TARGET_WRAPPER_FINAL + "/gradle-wrapper.jar") == wrapper.getJarFile().getAsFile().get()
        new File(getProject().getProjectDir(), "gradlew") == wrapper.getScriptFile().getAsFile().get()
        new File(getProject().getProjectDir(), "gradlew.bat") == wrapper.getBatchScript().getAsFile().get();
        GradleVersion.current().getVersion() == wrapper.getGradleVersion().get()
        Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME == wrapper.getDistributionPath().get()
        Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME == wrapper.getArchivePath().get()
        Wrapper.PathBase.GRADLE_USER_HOME == wrapper.getDistributionBase().get()
        Wrapper.PathBase.GRADLE_USER_HOME == wrapper.getArchiveBase().get()
        wrapper.getDistributionUrl().isPresent()
        wrapper.getDistributionSha256Sum().orNull == null
        !wrapper.getNetworkTimeout().isPresent()
        wrapper.getValidateDistributionUrl()
    }

    def "determines Windows script path from unix script path with #inName"() {
        when:
        wrapper.setScriptFile(getProject().file("build/$inName"))

        then:
        getProject().file("build/$outName") == wrapper.getBatchScript().getAsFile().get()

        where:
        inName           | outName
        "gradle.sh"      | "gradle.bat"
        "gradle-wrapper" | "gradle-wrapper.bat"
    }

    def "determines properties file path from jar path"() {
        given:
        wrapper.setJarFile(getProject().file("build/gradle-wrapper.jar"))

        expect:
        getProject().file("build/gradle-wrapper.properties") == wrapper.getPropertiesFile().getAsFile().get()
    }

    def "downloads for '#version' from repository "() {
        when:
        wrapper.setGradleVersion(version)
        wrapper.getValidateDistributionUrl().set(false)

        then:
        execute(wrapper)
        def properties = GUtil.loadProperties(expectedTargetWrapperProperties)
        properties.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY) == "https://services.gradle.org/distributions$snapshot/gradle-$out-bin.zip"

        where:
        version                     | out                         | snapshot
        "1.0-milestone-1"           | "1.0-milestone-1"           | ""
        "0.9.1-20101224110000+1100" | "0.9.1-20101224110000+1100" | "-snapshots"
        "0.9.1"                     | "0.9.1"                     | ""
        "latest"                    | RELEASE                     | ""
        "release-candidate"         | RELEASE_CANDIDATE           | ""
        "nightly"                   | NIGHTLY                     | "-snapshots"
        "release-nightly"           | RELEASE_NIGHTLY             | "-snapshots"
    }

    def "uses explicitly defined distribution url"() {
        given:
        wrapper.setGradleVersion("0.9")
        wrapper.setDistributionUrl("http://some-url")

        expect:
        "http://some-url" == wrapper.getDistributionUrl().get()
    }

    def "uses explicitly defined distribution sha256 sum"() {
        given:
        wrapper.setDistributionSha256Sum("somehash")

        expect:
        "somehash" == wrapper.getDistributionSha256Sum().get()
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

    def "execute with non extant wrapper jar parent directory"() {
        when:
        def unjarDir = temporaryFolder.createDir("unjar")
        execute(wrapper)
        expectedTargetWrapperJar.unzipToWithoutCheckingParentDirs(unjarDir)
        def properties = GUtil.loadProperties(expectedTargetWrapperProperties)

        then:
        unjarDir.file(GradleWrapperMain.class.getName().replace(".", "/") + ".class").assertIsFile()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY) == "https://services.gradle.org/distributions/gradle-1.0-bin.zip"
        properties.getProperty(WrapperExecutor.DISTRIBUTION_SHA_256_SUM) == wrapper.getDistributionSha256Sum().get()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY) == wrapper.getDistributionBase().get().toString()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY) == wrapper.getDistributionPath().get()
        properties.getProperty(WrapperExecutor.ZIP_STORE_BASE_PROPERTY) == wrapper.getArchiveBase().get().toString()
        properties.getProperty(WrapperExecutor.ZIP_STORE_PATH_PROPERTY) == wrapper.getArchivePath().get()
    }

    def "execute with networkTimeout set"() {
        given:
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
        properties.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY) == "https://services.gradle.org/distributions/gradle-1.0-bin.zip"
        properties.getProperty(WrapperExecutor.DISTRIBUTION_SHA_256_SUM) == wrapper.getDistributionSha256Sum().get()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY) == wrapper.getDistributionBase().get().toString()
        properties.getProperty(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY) == wrapper.getDistributionPath().get()
        properties.getProperty(WrapperExecutor.ZIP_STORE_BASE_PROPERTY) == wrapper.getArchiveBase().get().toString()
        properties.getProperty(WrapperExecutor.ZIP_STORE_PATH_PROPERTY) == wrapper.getArchivePath().get()
    }

    def "distributionUrl should not contain small dotless I letter when locale has small dotless I letter"() {
        given:
        Locale originalLocale = Locale.getDefault()
        Locale.setDefault(new Locale("tr","TR"))

        when:
        execute(wrapper)
        String distributionUrl = wrapper.getDistributionUrl()

        then:
        distributionUrl.contains("\u0131") == false

        cleanup:
        Locale.setDefault(originalLocale)
    }
}
