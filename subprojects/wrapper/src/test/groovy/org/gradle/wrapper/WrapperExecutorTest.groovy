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

import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification

class WrapperExecutorTest extends Specification {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    final Install install = Mock()
    final BootstrapMainStarter start = Mock()
    TestFile projectDir;
    TestFile propertiesFile;
    Properties properties = new Properties()

    def setup() {
        projectDir = tmpDir.dir
        propertiesFile = tmpDir.file('gradle/wrapper/gradle-wrapper.properties')

        properties.distributionUrl = 'http://server/test/gradle.zip'
        properties.distributionBase = 'testDistBase'
        properties.distributionPath = 'testDistPath'
        properties.zipStoreBase = 'testZipBase'
        properties.zipStorePath = 'testZipPath'
        propertiesFile.parentFile.mkdirs()
        propertiesFile.withOutputStream { properties.store(it, 'header') }
    }

    def "uses properties file to determine distribution"() {
        def wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out)

        expect:
        wrapper.distribution == new URI('http://server/test/gradle.zip')
    }

    def "loads wrapper meta data from project directory"() {
        def wrapper = WrapperExecutor.forProjectDirectory(projectDir, System.out)

        expect:
        wrapper.distribution == new URI('http://server/test/gradle.zip')
    }

    def "can query for distribution when properties file does not exist in project directory"() {
        def wrapper = WrapperExecutor.forProjectDirectory(tmpDir.file('unknown'), System.out)

        expect:
        wrapper.distribution == null
    }

    def "execute installs distribution and launches application"() {
        def wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out)
        def installDir = tmpDir.file('install')

        when:
        wrapper.execute(['arg'] as String[], install, start)

        then:
        1 * install.createDist(new URI('http://server/test/gradle.zip'), 'testDistBase', 'testDistPath', 'testZipBase', 'testZipPath') >> installDir
        1 * start.start(['arg'] as String[], installDir)
        0 * _._
    }

    def "fails when distribution not specified in properties file"() {
        def properties = new Properties()
        propertiesFile.withOutputStream { properties.store(it, 'header') }

        when:
        WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out)

        then:
        RuntimeException e = thrown()
        e.message == "Could not load wrapper properties from '$propertiesFile'."
        e.cause.message == "No value with key 'distributionUrl' specified in wrapper properties file '$propertiesFile'."
    }

    def "execute fails when properties file does not exist"() {
        propertiesFile.delete()
        def wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out)

        when:
        wrapper.execute(['arg'] as String[], install, start)

        then:
        FileNotFoundException e = thrown()
        e.message == "Wrapper properties file '$propertiesFile' does not exist."
    }

    def "allows old format of the wrapper"() {
        given:
        def properties = new Properties()

        properties.distributionBase = 'testDistBase'
        properties.distributionPath = 'testDistPath'
        properties.zipStoreBase = 'testZipBase'
        properties.zipStorePath = 'testZipPath'

        properties.urlRoot="http://gradle.artifactoryonline.com/gradle/distributions"
        properties.distributionVersion="1.0-milestone-3"
        properties.distributionName="gradle"
        properties.distributionClassifier="bin"

        propertiesFile.withOutputStream { properties.store(it, 'header') }

        when:
        WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out)

        then:
        noExceptionThrown()
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
        WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out)

        then:
        Exception e = thrown()
        e.cause.message.contains "key 'distributionUrl'"
    }

    def "supports relative distribution url"() {
        given:
        properties.distributionUrl = 'some/relative/url/to/bin.zip'
        propertiesFile.withOutputStream { properties.store(it, 'header') }

        when:
        WrapperExecutor wrapper = WrapperExecutor.forWrapperPropertiesFile(propertiesFile, System.out)

        then:
        //distribution uri should resolve into absolute path
        wrapper.distribution.schemeSpecificPart != 'some/relative/url/to/bin.zip'
        wrapper.distribution.schemeSpecificPart.endsWith 'some/relative/url/to/bin.zip'
    }
}
