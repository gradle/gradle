/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.wrapper

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class WrapperExecutorTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    final Install install = Mock()
    final BootstrapMainStarter start = Mock()
    TestFile projectDir;
    TestFile propertiesFile;
    Properties properties = new Properties()

    def setup() {
        projectDir = tmpDir.testDirectory
        propertiesFile = tmpDir.file('gradle/wrapper/gradle-wrapper.properties')

        properties.distributionUrl = 'http://server/test/gradle.zip'
        properties.distributionBase = 'testDistBase'
        properties.distributionPath = 'testDistPath'
        properties.zipStoreBase = 'testZipBase'
        properties.zipStorePath = 'testZipPath'
        properties.distributionSha256Sum = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
        properties.networkTimeout = '11000'
        properties.validateDistributionUrl = 'true'
        propertiesFile.parentFile.mkdirs()
        propertiesFile.withOutputStream { properties.store(it, 'header') }
    }

    def "loads wrapper meta data from specified properties file"() {
        def wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile)

        expect:
        wrapper.distribution == new URI('http://server/test/gradle.zip')
        wrapper.configuration.distribution == new URI('http://server/test/gradle.zip')
        wrapper.configuration.distributionBase == 'testDistBase'
        wrapper.configuration.distributionPath == 'testDistPath'
        wrapper.configuration.zipBase == 'testZipBase'
        wrapper.configuration.zipPath == 'testZipPath'
        wrapper.configuration.distributionSha256Sum == 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
        wrapper.configuration.networkTimeout == 11000
        wrapper.configuration.validateDistributionUrl
    }

    def "loads wrapper meta data from specified project directory"() {
        def wrapper = WrapperExecutor.forProjectDirectory(projectDir)

        expect:
        wrapper.distribution == new URI('http://server/test/gradle.zip')
        wrapper.configuration.distribution == new URI('http://server/test/gradle.zip')
        wrapper.configuration.distributionBase == 'testDistBase'
        wrapper.configuration.distributionPath == 'testDistPath'
        wrapper.configuration.zipBase == 'testZipBase'
        wrapper.configuration.zipPath == 'testZipPath'
        wrapper.configuration.distributionSha256Sum == 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
        wrapper.configuration.networkTimeout == 11000
        wrapper.configuration.validateDistributionUrl
    }

    def "uses default meta data when properties file does not exist in project directory"() {
        def wrapper = WrapperExecutor.forProjectDirectory(tmpDir.file('unknown'))

        expect:
        wrapper.distribution == null
        wrapper.configuration.distribution == null
        wrapper.configuration.distributionBase == PathAssembler.GRADLE_USER_HOME_STRING
        wrapper.configuration.distributionPath == Install.DEFAULT_DISTRIBUTION_PATH
        wrapper.configuration.zipBase == PathAssembler.GRADLE_USER_HOME_STRING
        wrapper.configuration.zipPath == Install.DEFAULT_DISTRIBUTION_PATH
        wrapper.configuration.distributionSha256Sum == null
        wrapper.configuration.networkTimeout == Download.DEFAULT_NETWORK_TIMEOUT_MILLISECONDS
        wrapper.configuration.validateDistributionUrl
    }

    def "properties file need contain only the distribution URL"() {
        given:
        def properties = new Properties()
        properties.distributionUrl = 'http://server/test/gradle.zip'
        propertiesFile.withOutputStream { properties.store(it, 'header') }

        def wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile)

        expect:
        wrapper.distribution == new URI("http://server/test/gradle.zip")
        wrapper.configuration.distribution == new URI("http://server/test/gradle.zip")
        wrapper.configuration.distributionBase == PathAssembler.GRADLE_USER_HOME_STRING
        wrapper.configuration.distributionPath == Install.DEFAULT_DISTRIBUTION_PATH
        wrapper.configuration.zipBase == PathAssembler.GRADLE_USER_HOME_STRING
        wrapper.configuration.zipPath == Install.DEFAULT_DISTRIBUTION_PATH
        wrapper.configuration.networkTimeout == Download.DEFAULT_NETWORK_TIMEOUT_MILLISECONDS
        wrapper.configuration.validateDistributionUrl
    }

    def "execute installs distribution and launches application"() {
        def wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile)
        def installDir = tmpDir.file('install')

        when:
        wrapper.execute(['arg'] as String[], install, start)

        then:
        1 * install.createDist(wrapper.configuration) >> installDir
        1 * start.start(['arg'] as String[], installDir)
        0 * _._
    }

    def "fails when distribution not specified in properties file"() {
        def properties = new Properties()
        propertiesFile.withOutputStream { properties.store(it, 'header') }

        when:
        WrapperExecutor.forWrapperPropertiesFile(propertiesFile)

        then:
        RuntimeException e = thrown()
        e.message == "Could not load wrapper properties from '$propertiesFile'."
        e.cause.message == "No value with key 'distributionUrl' specified in wrapper properties file '$propertiesFile'."
    }

    def "forWrapperPropertiesFile() fails when properties file does not exist"() {
        def propertiesFile = tmpDir.file("unknown.properties")

        when:
        WrapperExecutor.forWrapperPropertiesFile(propertiesFile)

        then:
        RuntimeException e = thrown()
        e.message == "Wrapper properties file '$propertiesFile' does not exist."
    }

    def "reports error when none of the valid formats are met"() {
        given:
        def properties = new Properties()

        properties.distributionBase = 'testDistBase'
        properties.distributionPath = 'testDistPath'
        properties.zipStoreBase = 'testZipBase'
        properties.zipStorePath = 'testZipPath'

        propertiesFile.withOutputStream { properties.store(it, 'header') }

        when:
        WrapperExecutor.forWrapperPropertiesFile(propertiesFile)

        then:
        Exception e = thrown()
        e.cause.message == "No value with key 'distributionUrl' specified in wrapper properties file '$propertiesFile'."
    }

    def "supports relative distribution url"() {
        given:
        properties.distributionUrl = 'some/relative/url/to/bin.zip'
        propertiesFile.withOutputStream { properties.store(it, 'header') }

        when:
        WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile)

        then:
        //distribution uri should resolve into absolute path
        wrapper.distribution.schemeSpecificPart != 'some/relative/url/to/bin.zip'
        wrapper.distribution.schemeSpecificPart.endsWith 'some/relative/url/to/bin.zip'
    }
}
