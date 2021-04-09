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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.GUtil
import org.gradle.util.GradleVersion
import org.gradle.util.internal.WrapUtil
import org.gradle.wrapper.GradleWrapperMain
import org.gradle.wrapper.WrapperExecutor

class WrapperTest extends AbstractTaskTest {
    private Wrapper wrapper
    private String targetWrapperJarPath
    private TestFile expectedTargetWrapperJar
    private File expectedTargetWrapperProperties

    def setup() {
        wrapper = createTask(Wrapper.class)
        wrapper.setGradleVersion("1.0")
        targetWrapperJarPath = "gradle/wrapper"
        expectedTargetWrapperJar = new TestFile(getProject().getProjectDir(),
                targetWrapperJarPath + "/gradle-wrapper.jar")
        expectedTargetWrapperProperties = new File(getProject().getProjectDir(),
                targetWrapperJarPath + "/gradle-wrapper.properties")
        new File(getProject().getProjectDir(), targetWrapperJarPath).mkdirs()
        wrapper.setDistributionPath("somepath")
        wrapper.setDistributionSha256Sum("somehash")
    }

    Wrapper getTask() {
        return wrapper
    }

    def "wrapper has correct defaults"() {
        given:
        wrapper = createTask(Wrapper.class)

        expect:
        new File(getProject().getProjectDir(), "gradle/wrapper/gradle-wrapper.jar") == wrapper.getJarFile()
        new File(getProject().getProjectDir(), "gradlew") == wrapper.getScriptFile()
        new File(getProject().getProjectDir(), "gradlew.bat") == wrapper.getBatchScript()
        GradleVersion.current().getVersion() == wrapper.getGradleVersion()
        Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME == wrapper.getDistributionPath()
        Wrapper.DEFAULT_DISTRIBUTION_PARENT_NAME == wrapper.getArchivePath()
        Wrapper.PathBase.GRADLE_USER_HOME == wrapper.getDistributionBase()
        Wrapper.PathBase.GRADLE_USER_HOME == wrapper.getArchiveBase()
        wrapper.getDistributionUrl() != null
        wrapper.getDistributionSha256Sum() == null
    }

    def "determines Windows script path from unix script path"() {
        given:
        wrapper.setScriptFile("build/gradle.sh")

        expect:
        getProject().file("build/gradle.bat") == wrapper.getBatchScript()

        when:
        wrapper.setScriptFile("build/gradle-wrapper")

        then:
        getProject().file("build/gradle-wrapper.bat") == wrapper.getBatchScript()
    }

    def "determines properties file path from jar path"() {
        given:
        wrapper.setJarFile("build/gradle-wrapper.jar")

        expect:
        getProject().file("build/gradle-wrapper.properties") == wrapper.getPropertiesFile()
    }

    def "downloads from release repository for release versions"() {
        given:
        wrapper.setGradleVersion("0.9.1")

        expect:
        "https://services.gradle.org/distributions/gradle-0.9.1-bin.zip" == wrapper.getDistributionUrl()
    }

    def "downloads from release repository for preview release versions"() {
        given:
        wrapper.setGradleVersion("1.0-milestone-1")

        expect:
        "https://services.gradle.org/distributions/gradle-1.0-milestone-1-bin.zip" == wrapper.getDistributionUrl()
    }

    def "downloads from snapshot repository for snapshot versions"() {
        given:
        wrapper.setGradleVersion("0.9.1-20101224110000+1100")

        expect:
        "https://services.gradle.org/distributions-snapshots/gradle-0.9.1-20101224110000+1100-bin.zip" == wrapper.getDistributionUrl()
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

    def "execute with non extant wrapper jar parent directory"() {
        when:
        def unjarDir = temporaryFolder.createDir("unjar")
        execute(wrapper)
        expectedTargetWrapperJar.unzipTo(unjarDir)
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

    def "check inputs"() {
        expect:
        TaskPropertyTestUtils.getProperties(wrapper).keySet() == WrapUtil.toSet(
            "distributionBase", "distributionPath", "distributionUrl", "distributionSha256Sum",
            "distributionType", "archiveBase", "archivePath", "gradleVersion")
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
        expectedTargetWrapperJar.unzipTo(unjarDir)
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
