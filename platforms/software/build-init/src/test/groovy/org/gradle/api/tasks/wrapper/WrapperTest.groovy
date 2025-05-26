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

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.resources.TextResourceFactory
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.TaskPropertyTestUtils
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.internal.DistributionLocator
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.WrapUtil
import org.gradle.wrapper.GradleWrapperMain
import org.gradle.wrapper.WrapperExecutor

import java.lang.reflect.Type

class WrapperTest extends AbstractTaskTest {
    private static final String TARGET_WRAPPER_FINAL = "gradle/wrapper"
    private Wrapper wrapper
    private TestFile expectedTargetWrapperJar
    private File expectedTargetWrapperProperties

    def setup() {
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
        when:
        wrapper.setGradleVersion(version)

        then:
        def downloadUrl = new VersionResolver(project).resolveDownloadUrl(version, urlSuffix)
        downloadUrl == null || downloadUrl == wrapper.getDistributionUrl()

        where:
        version                     | urlSuffix
        "8.14.1"                    | "8"
        "7.6-milestone-1"           | "7"
        "latest"                    | "current"
        "release-candidate"         | "release-candidate"
        "release-milestone"         | "milestone"
        "nightly"                   | "nightly"
        "release-nightly"           | "release-nightly"
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

    def "execute with non extant wrapper jar parent directory"() {
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
        !distributionUrl.contains("\u0131")

        cleanup:
        Locale.setDefault(originalLocale)
    }

    private static class VersionResolver {

        private final TextResourceFactory textResourceFactory

        VersionResolver(Project project) {
            this.textResourceFactory = project.getResources().getText()
        }

        def resolveDownloadUrl(String version, String urlSuffix) {
            String versionUrl = DistributionLocator.getBaseUrl() + "/versions/" + urlSuffix
            String json = textResourceFactory.fromUri(versionUrl).asString()

            Map<String, String> versionInfo = versionInfoFromJson(json, version)
            if (versionInfo.isEmpty()) {
                return null
            }
            return downloadUrlFromVersionInfo(versionInfo, version)
        }

        private static String downloadUrlFromVersionInfo(Map<String, String> versionInfo, String version) {
            def downloadUrl = versionInfo.get("downloadUrl")
            if (downloadUrl == null) {
                throw new GradleException("There is currently no version information available for version: " + version + ".")
            }
            return downloadUrl
        }

        private static Map<String, String> versionInfoFromJson(String json, String version) {
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(new TypeToken<List<Map<String, String>>>() {}.getType(), new MapListDeserializer())
                .create()

            List<Map<String, String>> list = gson.fromJson(json, new TypeToken<List<Map<String, String>>>() {}.getType())

            Map<String, String> versionInfo
            if (list.size() == 1) {
                versionInfo = list[0]
            } else {
                versionInfo = list.stream()
                    .filter { it.get("version") == version }
                    .findFirst()
                    .orElseThrow { new GradleException("Can't find version: " + version) }
            }
            versionInfo
        }

        static class MapListDeserializer implements JsonDeserializer<List<Map<String, String>>> {
            @Override
            List<Map<String, String>> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                List<Map<String, String>> list = new ArrayList<>()

                if (json.isJsonArray()) {
                    for (JsonElement element : json.getAsJsonArray()) {
                        Map<String, String> map = context.deserialize(element, new TypeToken<Map<String, String>>(){}.getType())
                        list.add(map)
                    }
                } else if (json.isJsonObject()) {
                    Map<String, String> map = context.deserialize(json, new TypeToken<Map<String, String>>(){}.getType())
                    list.add(map)
                }

                return list
            }
        }
    }
}
