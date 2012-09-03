/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.IvyRepository
import org.spockframework.util.TextUtil
import spock.lang.Issue

public class IvyLocalPublishIntegrationTest extends AbstractIntegrationSpec {
    public void canPublishToLocalFileRepository() {
        given:
        def repo = new IvyRepository(distribution.testFile('ivy-repo'))
        def module = repo.module("org.gradle", "publish", "2")

        def rootDir = TextUtil.escape(repo.rootDir.path)
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            url "${rootDir}"
        }
    }
}
"""
        when:
        succeeds 'uploadArchives'

        then:
        module.ivyFile.assertIsFile()
        module.assertChecksumPublishedFor(module.ivyFile)

        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
        module.assertChecksumPublishedFor(module.jarFile)
    }

    @Issue("GRADLE-2456")
    public void generatesSHA1FileWithLeadingZeros() {
        given:
        def repo = new IvyRepository(distribution.testFile('ivy-repo'))
        def module = repo.module("org.gradle", "publish", "2")
        byte[] jarBytes = [0, 0, 0, 5]
        def artifactFile = file("testfile.bin")
        artifactFile << jarBytes
        def rootDir = TextUtil.escape(repo.rootDir.path)
        def artifactPath = TextUtil.escape(artifactFile.path)
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin:'java'
group = "org.gradle"
version = '2'
artifacts {
        archives file: file("${artifactPath}"), name: 'testfile', type: 'bin'
}

uploadArchives {
    repositories {
        ivy {
            url "${rootDir}"
        }
    }
}
"""
        when:
        succeeds 'uploadArchives'

        then:
        def shaOneFile = module.moduleDir.file("testfile-2.bin.sha1")
        shaOneFile.exists()
        shaOneFile.text == "00e14c6ef59816760e2c9b5a57157e8ac9de4012"
    }

    @Issue("GRADLE-1811")
    public void canGenerateTheIvyXmlWithoutPublishing() {
        //this is more like documenting the current behavior.
        //Down the road we should add explicit task to create ivy.xml file

        given:
        buildFile << '''
apply plugin: 'java'

configurations {
  myJars
}

task myJar(type: Jar)

artifacts {
  'myJars' myJar
}

task ivyXml(type: Upload) {
  descriptorDestination = file('ivy.xml')
  uploadDescriptor = true
  configuration = configurations.myJars
}
'''
        when:
        succeeds 'ivyXml'

        then:
        file('ivy.xml').assertIsFile()
    }
}
