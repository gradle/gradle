/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.initialization.buildsrc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.Unroll

class BuildScriptClasspathIntegrationSpec extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    MavenHttpRepository repo

    def setup() {
        executer.requireDaemon()
        executer.requireIsolatedDaemons()
        repo = new MavenHttpRepository(server, mavenRepo)

        repo.module("commons-io", "commons-io", "1.4").publish().allowAll()
        repo.module("", "test", "1.3-BUILD-SNAPSHOT").allowAll()

        server.start()
    }

    @Unroll("jars on buildscript classpath can change (deleteIfExists: #deleteIfExists, loopNumber: #loopNumber)")
    def "jars on buildscript classpath can change"() {
        given:
        buildFile << '''
            buildscript {
                repositories { flatDir { dirs 'repo' }}
                dependencies { classpath name: 'test', version: '1.3-BUILD-SNAPSHOT' }
            }

            task hello {
                doLast {
                    println new org.gradle.test.BuildClass().message()
                }
            }
        '''
        ArtifactBuilder builder = artifactBuilder()
        File jarFile = file("repo/test-1.3-BUILD-SNAPSHOT.jar")

        when:
        builder.sourceFile("org/gradle/test/BuildClass.java").createFile().text = '''
            package org.gradle.test;
            public class BuildClass {
                public String message() { return "hello world"; }
            }
        '''
        builder.buildJar(jarFile, deleteIfExists)

        then:
        succeeds("hello")
        result.assertOutputContains("hello world")

        when:
        builder = artifactBuilder()
        builder.sourceFile("org/gradle/test/BuildClass.java").createFile().text = '''
            package org.gradle.test;
            public class BuildClass {
                public String message() { return "hello again"; }
            }
        '''
        builder.buildJar(jarFile, deleteIfExists)

        then:
        succeeds("hello")
        result.assertOutputContains("hello again")

        where:
        deleteIfExists << [false, true] * 3
        loopNumber << (1..6).toList()
    }

    def "build script classloader copies only non-cached jar files"() {
        given:
        buildFile << """
            buildscript {
                repositories {
                    flatDir { dirs 'repo' }
                    maven{ url "${repo.uri}" }
                }
                dependencies {
                    classpath name: 'test', version: '1.3-BUILD-SNAPSHOT'
                    classpath 'commons-io:commons-io:1.4@jar'
                }
            }

            task showBuildscript {
                doLast {
                    showUrls(getClass().getClassLoader())
                }
            }

            def showUrls(classloader) {
                if (classloader instanceof java.net.URLClassLoader) {
                    classloader.URLs.each { println ">>>" + it }
                }
                if (classloader.parent != null) {
                    showUrls(classloader.parent)
                }
            }
        """
        ArtifactBuilder builder = artifactBuilder()
        File jarFile = file("repo/test-1.3-BUILD-SNAPSHOT.jar")

        when:
        builder.sourceFile("org/gradle/test/BuildClass.java").createFile().text = '''
            package org.gradle.test;
            public class BuildClass {
                public String message() { return "hello world"; }
            }
        '''
        builder.buildJar(jarFile)

        then:
        succeeds("showBuildscript")
        inJarCache("test-1.3-BUILD-SNAPSHOT.jar")
        notInJarCache("commons-io-1.4.jar")
    }

    def "url connection caching is not disabled by default"() {
        given:
        buildFile << """
            task checkUrlConnectionCaching {
                doLast {
                    URL url = new URL("jar:file://valid_jar_url_syntax.jar!/")
                    URLConnection urlConnection = url.openConnection()
                    assert urlConnection.defaultUseCaches
                }
            }
        """

        expect:
        succeeds("checkUrlConnectionCaching")
    }

    def "jars with resources on buildscript classpath can change"() {
        given:
        buildFile << '''
            buildscript {
                repositories { flatDir { dirs 'repo' }}
                dependencies { classpath name: 'test', version: '1.3-BUILD-SNAPSHOT' }
            }

            task hello {
                doLast {
                    println new org.gradle.test.BuildClass().message()
                }
            }
        '''
        ArtifactBuilder builder = artifactBuilder()
        File jarFile = file("repo/test-1.3-BUILD-SNAPSHOT.jar")

        when:
        def originalSourceFile = builder.sourceFile("org/gradle/test/BuildClass.java")
        originalSourceFile.text = '''
            package org.gradle.test;
            import java.util.Properties;
            import java.io.IOException;
            import java.net.URL;
            public class BuildClass {
                public String message() throws IOException {
                    Properties props = new Properties();
                    URL resource = getClass().getResource("test.properties");
                    props.load(resource.openStream());
                    return props.getProperty("text", "PROPERTY NOT FOUND");
                }
            }
        '''
        builder.resourceFile("org/gradle/test/test.properties").createFile().text = "text=hello world"
        builder.buildJar(jarFile)

        then:
        succeeds("hello")
        result.assertOutputContains("hello world")

        when:
        builder = artifactBuilder()
        builder.sourceFile("org/gradle/test/BuildClass.java").text = originalSourceFile.text.replace("test.properties", "test2.properties")
        builder.resourceFile("org/gradle/test/test2.properties").createFile().text = "text=hello again"
        builder.resourceFile("org/gradle/test/test.properties").delete()
        builder.buildJar(jarFile)

        then:
        succeeds("hello")
        result.assertOutputContains("hello again")
    }

    void notInJarCache(String filename) {
        inJarCache(filename, false)
    }

    void inJarCache(String filename, boolean shouldBeFound=true) {
        String fullpath = result.output.readLines().find { it.matches(">>>file:.*${filename}") }
        assert fullpath.contains("/caches/jars-3/") == shouldBeFound
    }
}
