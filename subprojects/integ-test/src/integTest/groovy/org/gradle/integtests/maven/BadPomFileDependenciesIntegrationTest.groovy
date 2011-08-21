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
package org.gradle.integtests.maven

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*

import spock.lang.*

class BadPomFileDependenciesIntegrationTest extends AbstractIntegrationSpec {

    @Issue("http://issues.gradle.org/browse/GRADLE-1005")
    def "can handle self referencing dependency"() {
        given:
        file("settings.gradle") << "include 'producer', 'client'"

        and:
        file("producer", "build.gradle") << """
            apply plugin: "java"
            group = "group"
            archivesBaseName = "artifact"
            version = 1.0
        """

        file("producer", "src", "main", "java", "pkg", "Hello.java") << "package pkg; public class Hello { public static final String HELLO = \"hello\"; }"

        expect:
        succeeds "producer:jar"

        when:
        def repoDir = file("repo/group/artifact/1.0")
        repoDir.mkdirs()

        file("producer/build/libs/artifact-1.0.jar").copyTo(repoDir.file("artifact-1.0.jar"))

        repoDir.file("artifact-1.0.pom") << """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>group</groupId>
              <artifactId>artifact</artifactId>
              <name>artifact</name>
              <version>1.0</version>
              <dependencies>
                <dependency>
                  <groupId>group</groupId>
                  <artifactId>artifact</artifactId>
                  <version>1.0</version>
                </dependency>
              </dependencies>
            </project>
        """

        file("client", "build.gradle") << """
            apply plugin: "java"
            repositories {
                mavenRepo urls: "file://\${new File(rootDir, "repo").absolutePath}"
            }
            dependencies {
                compile "group:artifact:1.0"
            }
        """

        file("client", "src", "main", "java", "Test.java") << """public class Test {
            public static final String CONST = pkg.Hello.HELLO;
        }"""

        then:
        succeeds ":client:classes"
    }

}