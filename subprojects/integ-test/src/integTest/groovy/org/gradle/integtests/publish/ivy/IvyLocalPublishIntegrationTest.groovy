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
import org.gradle.integtests.fixtures.HttpServer
import org.junit.Rule
import spock.lang.Issue

public class IvyLocalPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()

    public void canPublishToLocalFileRepository() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << '''
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            url "build/repo/"
        }
    }
}
'''
        when:
        succeeds 'uploadArchives'

        then:
        def uploadedIvy = file('build/repo/org.gradle/publish/2/ivy-2.xml')
        uploadedIvy.assertIsFile()
        def uploadedJar = file('build/repo/org.gradle/publish/2/publish-2.jar')
        uploadedJar.assertIsCopyOf(file('build/libs/publish-2.jar'))
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
