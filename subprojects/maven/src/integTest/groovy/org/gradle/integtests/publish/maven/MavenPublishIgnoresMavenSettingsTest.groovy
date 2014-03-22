/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue

class MavenPublishIgnoresMavenSettingsTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()

    @Ignore
    @Issue("GRADLE-2681")
    def "gradle ignores maven mirror configuration for uploading archives"() {
        given:

        TestFile m2Home = temporaryFolder.createDir("m2_home");
        m2Home.file("conf/settings.xml").createFile().text = """
<settings>
  <mirrors>
    <mirror>
      <id>ACME</id>
      <name>ACME Central</name>
      <url>http://acme.maven.org/maven2</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
"""
        settingsFile << "rootProject.name = 'root'"
        executer.withEnvironmentVars(M2_HOME: m2Home.absolutePath)
        buildFile << """
   apply plugin: 'java'
   apply plugin: 'maven'
   group = 'group'
   version = '1.0'
   uploadArchives {
       repositories {
           mavenDeployer {
               repository(url: "${mavenRepo.uri}")
           }
       }
   }
   """
        when:
        run("uploadArchives")
        then:
        !result.output.contains("Uploading: group/root/1.0/root-1.0.jar to repository ACME at http://acme.maven.org/maven2")
    }
}