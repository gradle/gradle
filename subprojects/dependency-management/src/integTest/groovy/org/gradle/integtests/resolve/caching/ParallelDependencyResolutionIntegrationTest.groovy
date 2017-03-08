/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve.caching

import com.google.common.hash.Hashing
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf

import java.text.SimpleDateFormat

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)

    File mavenMetadata
    String lastVersion
    File pomFile, pomFileSha1
    File jarFile, jarFileSha1
    int buildNumber

    def setup() {
        executer.withArgument('--parallel')
        executer.withArgument('--max-workers=3') // needs to be set to the maximum number of expectConcurrentExecution() calls
        executer.withArgument('--info')

        metadataFile()
        createPomFile()
        createJarFile()

        executer.requireOwnGradleUserHomeDir()

    }

    private void createJarFile() {
        jarFile = temporaryFolder.createFile('dummy-1.0-SNAPSHOT.jar')
        jarFileSha1 = temporaryFolder.createFile('dummy-1.0-SNAPSHOT.jar.sha1')
        jarFileSha1.text = Hashing.sha1().hashBytes(jarFile.bytes).toString()
    }

    private void createPomFile() {
        pomFile = temporaryFolder.createFile('dummy-1.0-SNAPSHOT.pom')
        pomFile.setText("""<?xml version="1.0" encoding="UTF-8"?>
<project
  xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.acme</groupId>
  <artifactId>dummy</artifactId>
  <version>1.0-SNAPSHOT</version>
</project>""", "utf-8")
        pomFileSha1 = temporaryFolder.createFile('dummy-1.0-SNAPSHOT.pom.sha1')
        pomFileSha1.text = Hashing.sha1().hashBytes(pomFile.bytes).toString()
    }

    private void metadataFile() {
        mavenMetadata = temporaryFolder.createFile('maven-metadata.xml')
        def now = new Date()
        lastVersion = new SimpleDateFormat("yyyyMMdd.HHmmss").format(now)
        mavenMetadata.setText("""<?xml version="1.0" encoding="UTF-8"?>
<metadata>
  <groupId>com.acme</groupId>
  <artifactId>dummy</artifactId>
  <versioning>
    <snapshot>
      <timestamp>${lastVersion}</timestamp>
      <buildNumber>${buildNumber++}</buildNumber>
    </snapshot>
    <lastUpdated>${new SimpleDateFormat("yyyyMMddHHmmss").format(now)}</lastUpdated>
  </versioning>
</metadata>
""", "UTF-8")
    }

    def "dependency is only downloaded at most once per build"() {
        given:
        server.expectGet('/com/acme/dummy/1.0-SNAPSHOT/maven-metadata.xml', mavenMetadata)
        ('a'..'z').each {
            settingsFile << "include '$it'\n"
            file("${it}/build.gradle") << """
                apply plugin: 'java-library'
                
                repositories {
                    maven {
                        url '${server.address}'
                    }
                }

                dependencies {
                    implementation 'com.acme:dummy:1.0-SNAPSHOT'
                }

                task resolveDependencies {
                    doLast {
                        configurations.compileClasspath.resolve()
                    }
                }
            """
            server.allowGetOrHead('/com/acme/dummy/1.0-SNAPSHOT/maven-metadata.xml', mavenMetadata)
        }
        println "Last version : $lastVersion"

        expectGetPomFile()
        server.expectGet("/com/acme/dummy/1.0-SNAPSHOT/dummy-1.0-${lastVersion}-0.jar", jarFile)

        when:
        run 'resolveDependencies'

        then:
        noExceptionThrown()
    }

    private void expectGetPomFile() {
//        server.expectHead("/com/acme/dummy/1.0-SNAPSHOT/dummy-1.0-${lastVersion}-0.pom", pomFile)
        server.expectGet("/com/acme/dummy/1.0-SNAPSHOT/dummy-1.0-${lastVersion}-0.pom", pomFile)
//        server.expectGet("/com/acme/dummy/1.0-SNAPSHOT/dummy-1.0-${lastVersion}-0.pom.sha1", pomFileSha1)
    }
}
