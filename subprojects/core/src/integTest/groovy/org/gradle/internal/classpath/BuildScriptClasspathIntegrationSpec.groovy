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

package org.gradle.internal.classpath

import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

class BuildScriptClasspathIntegrationSpec extends AbstractIntegrationSpec implements FileAccessTimeJournalFixture {
    static final int MAX_CACHE_AGE_IN_DAYS = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES

    @Rule
    public final HttpServer server = new HttpServer()
    MavenHttpRepository repo

    def setup() {
        repo = new MavenHttpRepository(server, mavenRepo)

        repo.module("commons-io", "commons-io", "1.4").publish().allowAll()
        repo.module("", "test", "1.3-BUILD-SNAPSHOT").allowAll()

        server.start()
    }

    @Unroll("jars on buildscript classpath can change (loopNumber: #loopNumber)")
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
        builder.buildJar(jarFile)

        then:
        succeeds("hello")
        outputContains("hello world")

        when:
        builder = artifactBuilder()
        builder.sourceFile("org/gradle/test/BuildClass.java").createFile().text = '''
            package org.gradle.test;
            public class BuildClass {
                public String message() { return "hello again"; }
            }
        '''
        builder.buildJar(jarFile)

        then:
        succeeds("hello")
        outputContains("hello again")

        where:
        loopNumber << (1..6).toList()
    }

    @IgnoreIf({ AbstractGradleExecuter.agentInstrumentationEnabled }) // Agent-based instrumentation doesn't expose cached JARs
    def "build script classloader copies jar files to cache"() {
        given:
        createBuildFileThatPrintsClasspathURLs("""
            classpath name: 'test', version: '1.3-BUILD-SNAPSHOT'
            classpath 'commons-io:commons-io:1.4@jar'
        """)
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
        inJarCache("commons-io-1.4.jar")
    }

    private void createBuildFileThatPrintsClasspathURLs(String dependencies = '') {
        buildFile.text = """
            buildscript {
                repositories {
                    flatDir { dirs 'repo' }
                    maven { url "${repo.uri}" }
                }
                dependencies {
                    ${dependencies}
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
        executer.requireDaemon().requireIsolatedDaemons()

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
        outputContains("hello world")

        when:
        builder = artifactBuilder()
        builder.sourceFile("org/gradle/test/BuildClass.java").text = originalSourceFile.text.replace("test.properties", "test2.properties")
        builder.resourceFile("org/gradle/test/test2.properties").createFile().text = "text=hello again"
        builder.resourceFile("org/gradle/test/test.properties").delete()
        builder.buildJar(jarFile)

        then:
        succeeds("hello")
        outputContains("hello again")
    }

    def "cleans up unused cached JARs"() {
        given:
        executer.requireIsolatedDaemons() // needs to stop daemon
        requireOwnGradleUserHomeDir() // needs its own journal
        artifactBuilder().buildJar(file("repo/a-1.jar"))

        when:
        createBuildFileThatPrintsClasspathURLs("""
            classpath name: 'a', version: '1'
        """)
        succeeds("showBuildscript")

        then:
        def jar = inJarCache("a-1.jar").assertExists()
        journal.assertExists()

        when:
        run '--stop' // ensure daemon does not cache file access times in memory
        gcFile.lastModified = daysAgo(2)
        writeLastFileAccessTimeToJournal(jar.parentFile, daysAgo(MAX_CACHE_AGE_IN_DAYS + 1))

        and:
        createBuildFileThatPrintsClasspathURLs()
        // start as new process so journal is not restored from in-memory cache
        executer.withTasks("showBuildscript").start().waitForFinish()

        then:
        jar.assertDoesNotExist()

        when:
        createBuildFileThatPrintsClasspathURLs("""
            classpath name: 'a', version: '1'
        """)
        succeeds("showBuildscript")

        then:
        jar.assertExists()
    }

    def "cleans up unused versions of jars cache"() {
        given:
        requireOwnGradleUserHomeDir() // messes with caches
        def oldCacheDirs = [
            userHomeCacheDir.createDir("${DefaultClasspathTransformerCacheFactory.CACHE_NAME}-1"),
            userHomeCacheDir.createDir("${DefaultClasspathTransformerCacheFactory.CACHE_NAME}-2")
        ]
        gcFile.createFile().lastModified = daysAgo(2)

        when:
        succeeds("help")

        then:
        oldCacheDirs.each {
            it.assertDoesNotExist()
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/13816")
    def "classpath can contain badly formed jar"() {
        given:
        file("broken.jar") << "not a jar"
        buildFile << """
            buildscript { dependencies { classpath files("broken.jar") } }
        """

        when:
        succeeds()

        then:
        noExceptionThrown()
    }

    def "classpath can contain signed jar"() {
        given:
        buildFile << """
            buildscript {
                ${mavenCentralRepository()}
                dependencies {
                    classpath("org.bouncycastle:bcprov-jdk15on:1.66")
                }
            }

            tasks.register('checkSignature') {
                doLast {
                    def signedClass = org.bouncycastle.jce.provider.BouncyCastleProvider.class
                    assert signedClass.signers != null
                }
            }
        """

        when:
        succeeds 'checkSignature'

        then:
        noExceptionThrown()
    }

    void notInJarCache(String filename) {
        inJarCache(filename, false)
    }

    TestFile inJarCache(String filename, boolean shouldBeFound = true) {
        String fullpath = result.output.readLines().find { it.matches(">>>file:.*${filename}") }.replace(">>>", "")
        assert fullpath.startsWith(cacheDir.toURI().toString()) == shouldBeFound
        return new TestFile(new File(URI.create(fullpath)))
    }

    TestFile getGcFile() {
        return cacheDir.file("gc.properties")
    }

    TestFile getCacheDir() {
        return userHomeCacheDir.file(DefaultClasspathTransformerCacheFactory.CACHE_KEY)
    }


}
