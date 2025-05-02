/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.rocache

import groovy.transform.CompileStatic
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.cache.CachingIntegrationFixture
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
abstract class AbstractReadOnlyCacheDependencyResolutionTest extends AbstractHttpDependencyResolutionTest implements CachingIntegrationFixture {
    TestFile roCacheDir
    ResolveTestFixture resolve

    boolean isPublishJavadocsAndSources() {
        false
    }

    boolean isResolveDynamic() {
        false
    }

    abstract List<MavenHttpModule> getModulesInReadOnlyCache(MavenHttpRepository repo)

    def setup() {
        executer.requireIsolatedDaemons()
        executer.requireOwnGradleUserHomeDir()
        def roModules = getModulesInReadOnlyCache(mavenHttpRepo)
        roModules.each {
            it.withModuleMetadata()
            if (publishJavadocsAndSources) {
                it.withSourceAndJavadoc()
            }
            it.publish()
        }
        def deps = new StringBuilder()
        StringBuilder queries = new StringBuilder()
        roModules.each {
            expectResolve(it)
            it.metaData.allowGetOrHead()
            it.rootMetaData.allowGetOrHead()
            deps.append("""                implementation '${it.group}:${it.module}:${resolveDynamic ? '+' : it.version}'
""")
            if (publishJavadocsAndSources) {
                it.getArtifact(classifier: 'javadoc').allowGetOrHead()
                it.getArtifact(classifier: 'sources').allowGetOrHead()
                queries.append("""
                    dependencies.createArtifactResolutionQuery()
                       .forModule('${it.group}', '${it.module}', '${it.version}')
                       .withArtifacts(JvmLibrary, SourcesArtifact, JavadocArtifact)
                       .execute()
                    """)
            }
        }
        buildFile << """
            apply plugin: 'java-library'
            repositories { maven { url = "${mavenHttpRepo.uri}" } }
            dependencies {
                $deps
            }
            tasks.register("populateCache") {
                doLast {
                    configurations.compileClasspath.files
                    $queries
                }
            }
        """
        executer.withArgument("--no-configuration-cache") // task uses Configuration API
        run ":populateCache"
        executer.stop()
        copyToReadOnlyCache()
        buildFile.setText("")

        buildFile << """
            apply plugin: 'java-library'

            group = 'org.gradle'
            version = '20'

            repositories {
               maven {
                  url = "${mavenHttpRepo.uri}"
               }
            }

            tasks.register("extraArtifacts") {
                doLast {
                    $queries
                }
            }
        """
        mavenHttpRepo.server.resetExpectations()
        configureResolveTestFixture()

        settingsFile << """
            rootProject.name = 'ro-test'
        """
    }

    MavenHttpModule expectResolve(MavenHttpModule module) {
        module.pom.expectGet()
        module.moduleMetadata.expectGet()
        module.artifact.expectGet()
        module
    }

    private void configureResolveTestFixture() {
        def config = 'compileClasspath'
        resolve = new ResolveTestFixture(buildFile, config)
        resolve.prepare()
        resolve.expectDefaultConfiguration("api")
        buildFile << """
            allprojects {
                tasks.named("checkDeps") {
                    def outputFile = rootProject.file("\${rootProject.buildDir}/${config}-files.txt")
                    def files = configurations.${config}
                    doLast {
                        outputFile.withWriter { wrt ->
                            files.each { f ->
                                wrt.println("\${f.name}: \${f.toURI()}")
                            }
                        }
                    }
                }
            }
        """
    }

    Map<String, File> getResolvedArtifacts() {
        Map<String, File> result = [:]
        file("build/${resolve.config}-files.txt").eachLine {
            String[] spl = it.split(': ')
            result[spl[0]] = new File(new URI(spl[1]))
        }
        result
    }

    def cleanup() {
        checkIncubationMessage()
        makeCacheWritable()
    }

    protected void checkIncubationMessage() {
        outputContains("Shared read-only dependency cache is an incubating feature.")
    }

    void withReadOnlyCache() {
        executer.withReadOnlyCacheDir(roCacheDir)
        makeCacheReadOnly()
    }

    TestFile fileInReadReadOnlyCache(String path) {
        return roCacheDir.file(path)
    }

    private void copyToReadOnlyCache() {
        roCacheDir = temporaryFolder.createDir("read-only-cache")
        def cachePath = roCacheDir.toPath()
        doCopy(metadataCacheDir, cachePath, CacheLayout.MODULES)

        roCacheDir
    }

    private void doCopy(File cacheDir, Path cachePath, CacheLayout entry) {
        if (cacheDir.exists()) {
            Files.move(cacheDir.toPath(), cachePath.resolve(entry.key))
        }
    }

    void assertInReadOnlyCache(File file) {
        boolean inCache = isInRoCache(file)
        assert inCache: "File ${file} wasn't found in read-only cache"
    }

    private boolean isInRoCache(File file) {
        if (file == null) {
            throw new AssertionError("Expected file doesn't exist")
        }
        Path artifactFile = file.toPath()
        Path roCachePath = roCacheDir.toPath()
        boolean inCache = false
        while (artifactFile != null) {
            if (artifactFile == roCachePath) {
                inCache = true
                break
            }
            artifactFile = artifactFile.parent
        }
        inCache
    }

    void assertNotInReadOnlyCache(File file) {
        boolean inCache = isInRoCache(file)
        assert !inCache: "File ${file} was found in read-only cache"
    }

    void assertInReadOnlyCache(String... fileNames) {
        def artifacts = resolvedArtifacts
        fileNames.each { fileName ->
            assertInReadOnlyCache(artifacts[fileName])
        }
    }

    void assertNotInReadOnlyCache(String... fileNames) {
        def artifacts = resolvedArtifacts
        fileNames.each { fileName ->
            assertNotInReadOnlyCache(artifacts[fileName])
        }
    }

    private void makeCacheReadOnly() {
        makeWritable(false)
    }

    private void makeCacheWritable() {
        makeWritable(true)
    }

    private void makeWritable(boolean writable) {
        roCacheDir.listFiles().each {
            it.eachFileRecurse {
                it.setWritable(writable)
            }
        }
    }
}
