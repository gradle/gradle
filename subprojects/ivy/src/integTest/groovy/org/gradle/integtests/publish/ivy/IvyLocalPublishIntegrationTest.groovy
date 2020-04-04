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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.spockframework.util.TextUtil
import spock.lang.Issue
import spock.lang.Unroll

import static org.hamcrest.core.StringContains.containsString

class IvyLocalPublishIntegrationTest extends AbstractIntegrationSpec {
    @ToBeFixedForInstantExecution
    def canPublishToLocalFileRepository() {
        given:
        def module = ivyRepo.module("org.gradle", "publish", "2")

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
        }
    }
}
"""
        when:
        executer.expectDeprecationWarning()
        succeeds 'uploadArchives'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
    }

    @Issue("GRADLE-2456")
    @ToBeFixedForInstantExecution
    def generatesSHA1FileWithLeadingZeros() {
        given:
        def module = ivyRepo.module("org.gradle", "publish", "2")
        byte[] jarBytes = [0, 0, 0, 5]
        def artifactFile = file("testfile.bin")
        artifactFile << jarBytes
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
            url "${ivyRepo.uri}"
        }
    }
}
"""
        when:
        executer.expectDeprecationWarning()
        succeeds 'uploadArchives'

        then:
        def shaOneFile = module.moduleDir.file("testfile-2.bin.sha1")
        shaOneFile.exists()
        shaOneFile.text == "00e14c6ef59816760e2c9b5a57157e8ac9de4012"
    }

    @Issue("GRADLE-1811")
    def canGenerateTheIvyXmlWithoutPublishing() {
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

    // This test represents the state of the art, not the expected behavior (which remains to be spec'ed out)
    @Unroll
    @ToBeFixedForInstantExecution(because = ":uploadArchives")
    def "Generated ivy.xml file is not influenced by configuration attributes"() {
        given:
        buildFile << """
apply plugin: 'java'

def foo = Attribute.of('foo', String)
def baz = Attribute.of('baz', String)

configurations {
  myJars {
     $attributes
  }
}
dependencies {
  attributesSchema {
    attribute(foo)
    attribute(baz)
  }
  myJars 'a:b:1.2'
}

task myJar(type: Jar)

artifacts {
  myJars myJar
}

task ivyXml(type: Upload) {
  descriptorDestination = file('ivy.xml')
  uploadDescriptor = true
  configuration = configurations.myJars
}
"""
        when:
        succeeds 'ivyXml'

        then:
        file('ivy.xml').assertIsFile()
        file('ivy.xml').text.contains '<conf name="myJars" visibility="public"/>'
        file('ivy.xml').text.contains '<dependency org="a" name="b" rev="1.2" conf="myJars-&gt;default"/>'

        where:
        attributes << [
            '', // no attributes
            'attributes.attribute(foo, "bar")', // single attribute
            'attributes { attribute(foo, "bar"); attribute(baz, "baz") }' // multiple attributes
        ]
    }

    @ToBeFixedForInstantExecution
    def "succeeds if trying to publish a file without extension"() {
        def module = ivyRepo.module("org.gradle", "publish", "2")
        settingsFile << 'rootProject.name = "publish"'

        given:
        file('someDir/a') << 'some text'
        buildFile << """

        apply plugin: 'base'

        group = "org.gradle"
        version = '2'
        artifacts {
            archives file("someDir/a")
        }

        uploadArchives {
            repositories {
                ivy {
                    url "${ivyRepo.uri}"
                }
            }
        }

        """

        when:
        executer.expectDeprecationWarning()
        succeeds 'uploadArchives'

        then:
        def published = module.moduleDir.file("a-2")
        published.assertIsCopyOf(file('someDir/a'))
    }

    @ToBeFixedForInstantExecution
    def "fails gracefully if trying to publish a directory with ivy"() {

        given:
        file('someDir/a.txt') << 'some text'
        buildFile << """

        apply plugin: 'base'

        configurations {
            archives
        }

        artifacts {
            archives file("someDir")
        }

        """

        when:
        fails 'uploadArchives'

        then:
        failure.assertHasCause "Could not publish configuration 'archives'"
        failure.assertThatCause(containsString('Cannot publish a directory'))

    }

}
