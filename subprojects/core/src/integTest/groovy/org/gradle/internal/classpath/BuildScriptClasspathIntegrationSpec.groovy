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

import groovy.test.NotYetImplemented
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

import java.nio.file.Files
import java.util.stream.Collectors

import static org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache.Skip.INVESTIGATE
import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

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

    @ToBeFixedForConfigurationCache(skip = INVESTIGATE)
    def "build script classloader copies only non-cached jar files to cache"() {
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
        // A jar coming from some file repo is copied into the transformation cache and served from there.
        inArtifactTransformCache("test-1.3-BUILD-SNAPSHOT.jar")
        // A jar coming from remote repo is cached in the global modules cache and served from there.
        // It isn't copied into the transformation cache.
        // The transformed counterparts are not visible when printing classpath data.
        notInArtifactTransformCache("commons-io-1.4.jar")
    }

    private void createBuildFileThatPrintsClasspathURLs(String dependencies = '') {
        buildFile.text = """
            buildscript {
                repositories {
                    flatDir { dirs 'repo' }
                    maven { url = "${repo.uri}" }
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

    @ToBeFixedForConfigurationCache(skip = INVESTIGATE)
    def "cleans up unused cached entries in the Jars cache"() {
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
        def buildscriptClasses = inJarCache("proj/").assertExists()
        journal.assertExists()

        when:
        run '--stop' // ensure daemon does not cache file access times in memory
        jarsCacheGcFile.lastModified = daysAgo(2)
        writeLastFileAccessTimeToJournal(buildscriptClasses.parentFile, daysAgo(MAX_CACHE_AGE_IN_DAYS + 1))

        and:
        createBuildFileThatPrintsClasspathURLs()
        // start as new process so journal is not restored from in-memory cache
        executer.withTasks("showBuildscript").start().waitForFinish()

        then:
        buildscriptClasses.assertDoesNotExist()

        when:
        createBuildFileThatPrintsClasspathURLs("""
            classpath name: 'a', version: '1'
        """)
        succeeds("showBuildscript")

        then:
        buildscriptClasses.assertExists()
    }

    def "cleans up unused versions of jars cache"() {
        given:
        requireOwnGradleUserHomeDir() // messes with caches
        def oldCacheDirs = [
            userHomeCacheDir.createDir("${DefaultClasspathTransformerCacheFactory.CACHE_NAME}-1"),
            userHomeCacheDir.createDir("${DefaultClasspathTransformerCacheFactory.CACHE_NAME}-2")
        ]
        jarsCacheGcFile.createFile().lastModified = daysAgo(2)

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
                    classpath("org.bouncycastle:bcprov-jdk18on:1.77")
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

    @Requires([IntegTestPreconditions.Java8HomeAvailable, IntegTestPreconditions.Java11HomeAvailable])
    def "proper version is selected for multi-release jar"() {
        given:
        createDir("mrjar") {
            file("build.gradle") << """
                plugins {
                    id("java")
                    id 'me.champeau.mrjar' version "0.1.1"
                }

                multiRelease {
                    targetVersions 8, 11
                }
            """
            file("src/main/java/org/gradle/test/mrjar/Foo.java") << """
                package org.gradle.test.mrjar;

                public class Foo {
                    public static String getBar() {
                        return "DEFAULT";
                    }
                }
            """
            file("src/java11/java/org/gradle/test/mrjar/Foo.java") << """
                package org.gradle.test.mrjar;

                public class Foo {
                    public static String getBar() {
                        return "11";
                    }
                }
            """
        }

        buildFile("""
            buildscript {
                dependencies {
                    classpath "org.gradle.test:mrjar:1.+"
                }
            }

            import org.gradle.test.mrjar.Foo

            tasks.register("printFoo") {
                doLast {
                    println("JAR = \${Foo.bar}")
                }
            }
        """)
        settingsFile("""
            includeBuild("mrjar") {
                dependencySubstitution {
                    substitute module('org.gradle.test:mrjar') using project(':')
                }
            }
        """)

        def jdk8 = AvailableJavaHomes.getJdk8()
        def jdk11 = AvailableJavaHomes.getJdk11()

        when:
        executer.withJvm(jdk8).withArguments("-Porg.gradle.java.installations.paths=${jdk8.javaHome},${jdk11.javaHome}")
        succeeds("printFoo")

        then:
        outputContains("JAR = DEFAULT")

        when:
        executer.withJvm(jdk11).withArguments("-Porg.gradle.java.installations.paths=${jdk8.javaHome},${jdk11.javaHome}")
        succeeds("printFoo")

        then:
        outputContains("JAR = 11")
    }

    def "class with #lambdaCount lambdas can be instrumented"() {
        given:
        createDir("buildSrc/src/main/java") {
            try(def src = file("ManyLambdas.java").newWriter()) {
                src.append("""
                    import ${List.name};
                    import ${ArrayList.name};

                    public class ManyLambdas {
                        public List<Runnable> createLotsOfLambdas() {
                            List<Runnable> runnables = new ArrayList<>($lambdaCount);
                """)
                for (int i = 1; i <= lambdaCount; ++i) {
                    src.append("""
                            runnables.add(() -> System.out.println("lambda #" + $i));
                    """)
                }
                src.append("""
                            return runnables;
                        }
                    }
                """)
            }
        }
        buildFile("""
            abstract class LambdaTask extends DefaultTask {
                @Input
                abstract ListProperty<Runnable> getMyActions()

                @Input
                abstract Property<Class<?>> getActionClass()

                @TaskAction
                def printLambdaCount() {
                    println("generated method count = \${getDeserializeMethodsCount(actionClass.get())}")
                }

                def getDeserializeMethodsCount(Class<?> cls) {
                    return Arrays.stream(cls.getDeclaredMethods()).filter {
                        it.name.startsWith('\$deserializeLambda')
                    }.count()
                }

                @TaskAction
                def runMyActions() {
                    myActions.get().forEach {
                        it.run()
                    }
                }
            }

            tasks.register("lambda", LambdaTask) {
                myActions = new ManyLambdas().createLotsOfLambdas()
                actionClass = ManyLambdas
            }
        """)

        when:
        succeeds("lambda")

        then:
        outputContains("generated method count = $expectedMethodCount")
        outputContains("lambda #1")
        outputContains("lambda #$lambdaCount")

        where:
        lambdaCount || expectedMethodCount
        1000        || 1
        2000        || 2
        3200        || 3
    }

    @NotYetImplemented
    // Instrumentation with artifact transform doesn't support that yet
    def "transformation normalizes input jars before fingerprinting"() {
        requireOwnGradleUserHomeDir() // inspects cached content

        given:
        def buildClassSource = '''
            package org.gradle.test;
            public class BuildClass {
                public String message() { return "hello world"; }
            }
        '''
        def reproducibleJar = file("reproducible/testClasses.jar")
        def currentTimestampJar = file("current/testClasses.jar")
        artifactBuilder().tap {
            preserveTimestamps(false)
            sourceFile("org/gradle/test/BuildClass.java").text = buildClassSource
            buildJar(reproducibleJar)
        }
        artifactBuilder().tap {
            preserveTimestamps(true)
            sourceFile("org/gradle/test/BuildClass.java").text = buildClassSource
            buildJar(currentTimestampJar)
        }

        Closure<String> subprojectSource = {File jarPath -> """
            buildscript { dependencies { classpath files("${normaliseFileSeparators(jarPath.absolutePath)}") } }

            tasks.register("printMessage") { doLast { println (new org.gradle.test.BuildClass().message()) } }
        """}

        settingsFile """
            include "reproducible", "current"
        """

        file("reproducible/build.gradle").text = subprojectSource(reproducibleJar)
        file("current/build.gradle").text = subprojectSource(currentTimestampJar)

        expect:
        succeeds("printMessage", "--info")

        getArtifactTransformJarsByName("original/testClasses.jar").size() == 1
        getArtifactTransformJarsByName("instrumented/testClasses.jar").size() == 1
    }

    void notInJarCache(String filename) {
        inJarCache(filename, false)
    }

    TestFile inJarCache(String filename, boolean shouldBeFound = true) {
        String fullpath = result.output.readLines().find { it.matches(">>>file:.*${filename}") }.replace(">>>", "")
        assert fullpath.startsWith(jarsCacheDir.toURI().toString()) == shouldBeFound
        return new TestFile(new File(URI.create(fullpath)))
    }

    TestFile notInArtifactTransformCache(String filename) {
        inArtifactTransformCache(filename, false)
    }

    TestFile inArtifactTransformCache(String filename, boolean shouldBeFound = true) {
        String fullpath = result.output.readLines().find { it.matches(">>>file:.*${filename}") }.replace(">>>", "")
        assert fullpath.startsWith(artifactTransformCacheDir.toURI().toString()) == shouldBeFound
        return new TestFile(new File(URI.create(fullpath)))
    }


    TestFile getJarsCacheGcFile() {
        return jarsCacheDir.file("gc.properties")
    }

    TestFile getJarsCacheDir() {
        return userHomeCacheDir.file(DefaultClasspathTransformerCacheFactory.CACHE_KEY)
    }

    TestFile getArtifactTransformCacheDir() {
        return getGradleVersionedCacheDir().file(CacheLayout.TRANSFORMS.getName())
    }

    /**
     * Finds all cached transformed JARs named {@code jarName}.
     * @param jarName the name of the JAR to look
     * @return the list of transformed JARs in the cache
     */
    List<File> getArtifactTransformJarsByName(String jarName) {
        return Files.find(artifactTransformCacheDir.toPath(), Integer.MAX_VALUE, (path, attributes) -> normaliseFileSeparators(path.toString()).endsWith(jarName))
            .map { new TestFile(it.toFile()) }
            .collect(Collectors.toList())
    }

    private static boolean isCachedTransformedEntryDir(File cacheChild) {
        return cacheChild.isDirectory() && !isCachedOriginalEntryDir(cacheChild)
    }

    private static boolean isCachedOriginalEntryDir(File cacheChild) {
        // Cached original JARs live in directories named like o_cc87da7c824fed55002d15744c8fba93, where the name is the fingerprint of the original JAR with "o_" prefix.
        // See CopyingClasspathFileTransformer.
        return cacheChild.isDirectory() && cacheChild.name.startsWith("o_")
    }
}
