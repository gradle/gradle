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

package org.gradle.normalization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import java.util.jar.Attributes
import java.util.jar.Manifest

class ConfigureRuntimeClasspathNormalizationIntegrationTest extends AbstractIntegrationSpec {
    def "can ignore files on runtime classpath in #tree (using runtime API: #api)"() {
        def project = new ProjectWithRuntimeClasspathNormalization(api).withFilesIgnored()

        def ignoredResource = project[ignoredResourceName]
        def notIgnoredResource = project[notIgnoredResourceName]

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        ignoredResource.changeContents()
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        notIgnoredResource.changeContents()
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        ignoredResource.remove()
        succeeds project.customTask

        then:
        skipped(project.customTask)

        when:
        ignoredResource.add()
        succeeds project.customTask

        then:
        skipped(project.customTask)

        where:
        tree                 | ignoredResourceName               | notIgnoredResourceName               | api
        'directories'        | 'ignoredResourceInDirectory'      | 'notIgnoredResourceInDirectory'      | Api.RUNTIME
        'jars'               | 'ignoredResourceInJar'            | 'notIgnoredResourceInJar'            | Api.RUNTIME
        'nested jars'        | 'ignoredResourceInNestedJar'      | 'notIgnoredResourceInNestedJar'      | Api.RUNTIME
        'nested in dir jars' | 'ignoredResourceInNestedInDirJar' | 'notIgnoredResourceInNestedInDirJar' | Api.RUNTIME
        'directories'        | 'ignoredResourceInDirectory'      | 'notIgnoredResourceInDirectory'      | Api.ANNOTATION
        'jars'               | 'ignoredResourceInJar'            | 'notIgnoredResourceInJar'            | Api.ANNOTATION
        'nested jars'        | 'ignoredResourceInNestedJar'      | 'notIgnoredResourceInNestedJar'      | Api.ANNOTATION
        'nested in dir jars' | 'ignoredResourceInNestedInDirJar' | 'notIgnoredResourceInNestedInDirJar' | Api.ANNOTATION
    }

    def "can ignore manifest attributes in #tree on runtime classpath"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME).withManifestAttributesIgnored()
        def manifestResource = project[resourceName]

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        manifestResource.changeAttributes((IMPLEMENTATION_VERSION): "1.0.1")
        succeeds project.customTask
        then:
        skipped(project.customTask)

        where:
        tree        | resourceName
        'jar'       | 'jarManifest'
        'directory' | 'manifestInDirectory'
    }

    def "can ignore entire manifest in #tree on runtime classpath"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME).withManifestIgnored()
        def manifestResource = project[resourceName]

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        manifestResource.changeAttributes((IMPLEMENTATION_VERSION): "1.0.1")
        succeeds project.customTask
        then:
        skipped(project.customTask)

        where:
        tree        | resourceName
        'jar'       | 'jarManifest'
        'directory' | 'manifestInDirectory'
    }

    def "can ignore all meta-inf files in #tree on runtime classpath"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME).withAllMetaInfIgnored()
        def manifestResource = project[resourceName]

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        manifestResource.changeAttributes((IMPLEMENTATION_VERSION): "1.0.1")
        project.jarManifestProperties.replaceContents("implementation-version=1.0.1")
        succeeds project.customTask
        then:
        skipped(project.customTask)

        where:
        tree        | resourceName
        'jar'       | 'jarManifest'
        'directory' | 'manifestInDirectory'
    }

    def "can ignore manifest properties on runtime classpath"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME).withManifestPropertiesIgnored()

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.jarManifestProperties.replaceContents("implementation-version=1.0.1")
        succeeds project.customTask
        then:
        skipped(project.customTask)
    }

    def "can configure ignore rules per project (using runtime API: #api)"() {
        def projectWithIgnores = new ProjectWithRuntimeClasspathNormalization('a', api).withFilesIgnored()
        def projectWithoutIgnores = new ProjectWithRuntimeClasspathNormalization('b', api)
        def allProjects = [projectWithoutIgnores, projectWithIgnores]
        settingsFile << "include 'a', 'b'"

        when:
        succeeds(*allProjects*.customTask)
        then:
        executedAndNotSkipped(*allProjects*.customTask)

        when:
        projectWithIgnores.ignoredResourceInJar.changeContents()
        projectWithoutIgnores.ignoredResourceInJar.changeContents()
        succeeds(*allProjects*.customTask)
        then:
        skipped(projectWithIgnores.customTask)
        executedAndNotSkipped(projectWithoutIgnores.customTask)

        where:
        api << [Api.RUNTIME, Api.ANNOTATION]
    }

    @UnsupportedWithConfigurationCache(because = "Task.getProject() during execution")
    def "runtime classpath normalization to #change cannot be changed after first usage (using runtime API: #api)"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME)
        project.buildFile << """
            task configureNormalization() {
                dependsOn '${project.customTask}'
                doLast {
                    project.normalization {
                        runtimeClasspath {
                            ${config}
                        }
                    }
                }
            }
        """.stripIndent()

        when:
        fails 'configureNormalization'

        then:
        failureHasCause 'Cannot configure runtime classpath normalization after execution started.'

        where:
        change                      | config                                                    | api
        'ignore file'               | "ignore '**/some-other-file.txt'"                         | Api.RUNTIME
        'ignore file'               | "ignore '**/some-other-file.txt'"                         | Api.ANNOTATION
        'ignore manifest attribute' | "metaInf { ignoreAttribute '${IMPLEMENTATION_VERSION}' }" | Api.RUNTIME
        'ignore manifest attribute' | "metaInf { ignoreAttribute '${IMPLEMENTATION_VERSION}' }" | Api.ANNOTATION
        'ignore property'           | "properties { ignoreProperty 'timestamp' }"               | Api.RUNTIME
        'ignore property'           | "properties { ignoreProperty 'timestamp' }"               | Api.ANNOTATION
    }

    def "can ignore properties on runtime classpath in #tree (using runtime API: #api)"() {
        def project = new ProjectWithRuntimeClasspathNormalization(api).withPropertiesIgnored()

        def ignoredResource = project[ignoredResourceName]

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        ignoredResource.changeProperty(IGNORE_ME, 'please ignore me')
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        ignoredResource.changeProperty(DONT_IGNORE_ME, 'please dont ignore me')
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        where:
        tree                 | ignoredResourceName              | api
        'directories'        | 'propertiesFileInDir'            | Api.RUNTIME
        'jars'               | 'propertiesFileInJar'            | Api.RUNTIME
        'nested jars'        | 'propertiesFileInNestedJar'      | Api.RUNTIME
        'nested in dir jars' | 'propertiesFileInNestedInDirJar' | Api.RUNTIME
        'directories'        | 'propertiesFileInDir'            | Api.ANNOTATION
        'jars'               | 'propertiesFileInJar'            | Api.ANNOTATION
        'nested jars'        | 'propertiesFileInNestedJar'      | Api.ANNOTATION
        'nested in dir jars' | 'propertiesFileInNestedInDirJar' | Api.ANNOTATION
    }

    def "can ignore properties in selected files"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME)
        def notIgnoredPropertiesFile = new PropertiesResource(project.root.file('classpath/dirEntry/bar.properties'), [(IGNORE_ME_TOO): 'this should not actually be ignored'])
        project.buildFile << """
            normalization {
                runtimeClasspath {
                    properties('**/foo.properties') {
                        ignoreProperty '${IGNORE_ME}'
                    }
                }
            }
        """

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.propertiesFileInDir.changeProperty(IGNORE_ME, 'please ignore me')
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        notIgnoredPropertiesFile.changeProperty(IGNORE_ME, 'please dont ignore me')
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)
    }

    def "can ignore properties in selected files defined in multiple rules"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME)
        def notIgnoredPropertiesFile = new PropertiesResource(project.root.file('classpath/dirEntry/bar.properties'), [(IGNORE_ME_TOO): 'this should not actually be ignored'])
        project.propertiesFileInDir.changeProperty(IGNORE_ME_TOO, 'this should also be ignored')
        project.buildFile << """
            normalization {
                runtimeClasspath {
                    properties('**/foo.properties') {
                        ignoreProperty '${IGNORE_ME}'
                    }
                    properties('some/path/to/foo.properties') {
                        ignoreProperty '${IGNORE_ME_TOO}'
                    }
                }
            }
        """

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.propertiesFileInDir.changeProperty(IGNORE_ME, 'please ignore me')
        project.propertiesFileInDir.changeProperty(IGNORE_ME_TOO, 'please ignore me too')
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        notIgnoredPropertiesFile.changeProperty(IGNORE_ME, 'please dont ignore me')
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "property ordering is consistent"() {
        def differentJdk = AvailableJavaHomes.differentJdk
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME)
        (1..100).each { index ->
            buildFile << """
                normalization {
                    runtimeClasspath {
                        properties('**/$index/foo.properties') {
                            ignoreProperty '${IGNORE_ME}'
                        }
                    }
                }
            """
        }

        when:
        withBuildCache().succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        run "clean"
        executer.withJavaHome(differentJdk.javaHome)
        withBuildCache().succeeds project.customTask
        then:
        skipped(project.customTask)
    }

    def "properties files are normalized against changes to whitespace, order and comments"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME)
        project.propertiesFileInDir.setContent('''
            foo=bar
            bar=baz
            fizz=fuzz
        '''.stripIndent())

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.propertiesFileInDir
            .setComment('this comment should be ignored')
            .setContent('''

                # Some comment
                bar=baz

                fizz=fuzz

                # Another comment
                foo=bar

            '''.stripIndent())
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.propertiesFileInDir.changeProperty('foo', 'baz')
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)
    }

    def "can add rules to the default properties rule"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME)
        def notIgnoredPropertiesFile = new PropertiesResource(project.root.file('classpath/dirEntry/bar.properties'), [(IGNORE_ME): 'this should not actually be ignored'])
        project.propertiesFileInDir.changeProperty(IGNORE_ME_TOO, 'this should also be ignored')
        project.buildFile << """
            normalization {
                runtimeClasspath {
                    properties('**/foo.properties') {
                        ignoreProperty '${IGNORE_ME}'
                    }
                    properties {
                        ignoreProperty '${IGNORE_ME_TOO}'
                    }
                }
            }
        """

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project.propertiesFileInDir.changeProperty(IGNORE_ME, 'please ignore me')
        project.propertiesFileInDir.changeProperty(IGNORE_ME_TOO, 'please ignore me too')
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        notIgnoredPropertiesFile.changeProperty(IGNORE_ME, 'please dont ignore me')
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)
    }

    def "safely handles properties files in #description with bad unicode escape sequences"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME).withFilesIgnored()
        project[resource].writeRaw('propertyWithBadValue=this is a bad unicode sequence'.bytes, [0x5C, 0x75, 0x78, 0x78, 0x78, 0x78] as byte[])
        project.buildFile << """
            normalization {
                runtimeClasspath {
                    properties('**/foo.properties') {
                        ignoreProperty '${IGNORE_ME}'
                    }
                }
            }
        """

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project[resource].writeRaw('propertyWithBadValue=this is also a bad unicode sequence '.bytes, [0x5C, 0x75, 0x79, 0x79, 0x79, 0x79] as byte[])
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        project[ignoredResource].changeContents()

        succeeds project.customTask
        then:
        skipped(project.customTask)

        where:
        description        | resource                    | ignoredResource
        "directories"      | "propertiesFileInDir"       | "ignoredResourceInDirectory"
        "zip files"        | "propertiesFileInJar"       | "ignoredResourceInJar"
        "nested zip files" | "propertiesFileInNestedJar" | "ignoredResourceInNestedJar"
    }

    def "safely handles manifests in #description with bad attributes"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME).withManifestPropertiesIgnored().withFilesIgnored()
        def badAttribute = 'Created-By: ' + ('x' * 512)
        project[resource].replaceContents(badAttribute)

        when:
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        succeeds project.customTask
        then:
        skipped(project.customTask)

        when:
        project[resource].replaceContents(badAttribute.replace('x', 'y'))
        succeeds project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        project[ignoredResource].changeContents()

        succeeds project.customTask
        then:
        skipped(project.customTask)

        where:
        description        | resource              | ignoredResource
        "directories"      | "manifestInDirectory" | "ignoredResourceInDirectory"
        "zip files"        | "jarManifest"         | "ignoredResourceInJar"
        "nested zip files" | "nestedJarManifest"   | "ignoredResourceInNestedJar"
    }

    @Issue('https://github.com/gradle/gradle/issues/16144')
    def "changing normalization configuration rules changes build cache key (#description)"() {
        def project = new ProjectWithRuntimeClasspathNormalization(Api.RUNTIME)
        project.propertiesFileInJar.changeProperty(ALSO_IGNORE_ME, 'some value')

        // We implement this with a flag, rather than just changing the build script, because we don't want the change in build script to affect
        // the cache hit. We want the only change to be in the normalization rules so we can be sure that's what's changing the cache key.
        project.buildFile << """
            normalization {
                runtimeClasspath {
                    if (providers.gradleProperty('${enableFilterFlag}').present) {
                        ${normalizationRule}
                    }
                }
            }
        """

        when:
        args('--build-cache')
        succeeds 'clean', project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        when:
        args("-P${enableFilterFlag}", '--build-cache')
        succeeds 'clean', project.customTask
        then:
        executedAndNotSkipped(project.customTask)

        where:
        enableFilterFlag        | normalizationRule                                                         | description
        PROPERTIES_FILTER_FLAG  | "properties('**/foo.properties') { ignoreProperty '${ALSO_IGNORE_ME}' }"  | 'properties rule'
        META_INF_FILTER_FLAG    | "metaInf { ignoreAttribute '${IMPLEMENTATION_VERSION}' }"                 | 'meta-inf rule'
        FILE_FILTER_FLAG        | "ignore '**/ignored.txt'"                                                 | 'ignore rule'
    }

    def "treats '#extension' as archive"() {
        def archive = file("archive.${extension}")
        def contents = file("archiveContents").createDir()
        def ignoredFile = contents.file("ignored.txt")
        ignoredFile << "this file is ignored"
        def nonIgnoredFile = contents.file("not-ignored.txt")
        nonIgnoredFile << "this file is not ignored"
        contents.zipTo(archive)
        buildFile << """
            normalization {
                runtimeClasspath {
                    ignore 'ignored.txt'
                }
            }

            task customTask {
                def outputFile = file("build/output.txt")
                inputs.file("${archive.name}")
                    .withPropertyName("classpath")
                    .withNormalizer(ClasspathNormalizer)
                outputs.file(outputFile)
                    .withPropertyName("outputFile")
                outputs.cacheIf { true }

                doLast {
                    outputFile.text = "done"
                }
            }
        """
        when:
        run "customTask"
        then:
        executedAndNotSkipped(":customTask")

        when:
        ignoredFile.text = "changed"
        archive.delete()
        contents.zipTo(archive)
        run "customTask"
        then:
        skipped(":customTask")

        where:
        extension << ["zip", "jar", "war", "rar", "ear", "apk", "aar", "klib"]
    }

    def "does not treat 'any' extension as archive"() {
        def archive = file("archive.any")
        def contents = file("archiveContents").createDir()
        def ignoredFile = contents.file("ignored.txt")
        ignoredFile << "this file is ignored"
        def nonIgnoredFile = contents.file("not-ignored.txt")
        nonIgnoredFile << "this file is not ignored"
        contents.zipTo(archive)
        buildFile << """
            normalization {
                runtimeClasspath {
                    ignore 'ignored.txt'
                }
            }

            task customTask {
                def outputFile = file("build/output.txt")
                inputs.file("${archive.name}")
                    .withPropertyName("classpath")
                    .withNormalizer(ClasspathNormalizer)
                outputs.file(outputFile)
                    .withPropertyName("outputFile")
                outputs.cacheIf { true }

                doLast {
                    outputFile.text = "done"
                }
            }
        """
        when:
        run "customTask"
        then:
        executedAndNotSkipped(":customTask")

        when:
        ignoredFile.text = "changed"
        archive.delete()
        contents.zipTo(archive)
        run "customTask"
        then:
        executedAndNotSkipped(":customTask")
    }

    static final String IGNORE_ME = 'ignore-me'
    static final String ALSO_IGNORE_ME = 'also-ignore-me'
    static final String IGNORE_ME_TOO = 'ignore-me-too'
    static final String DONT_IGNORE_ME = 'dont-ignore-me'
    static final String IMPLEMENTATION_VERSION = Attributes.Name.IMPLEMENTATION_VERSION.toString()
    static final String PROPERTIES_FILTER_FLAG = "filterProperties"
    static final String META_INF_FILTER_FLAG = "filterMetaInf"
    static final String FILE_FILTER_FLAG = "filterFile"

    enum Api {
        RUNTIME, ANNOTATION
    }

    class ProjectWithRuntimeClasspathNormalization {
        final TestFile root
        final TestFile buildCacheDir
        TestResource ignoredResourceInDirectory
        TestResource notIgnoredResourceInDirectory
        TestResource ignoredResourceInJar
        TestResource ignoredResourceInNestedJar
        TestResource ignoredResourceInNestedInDirJar
        TestResource notIgnoredResourceInJar
        TestResource notIgnoredResourceInNestedJar
        TestResource notIgnoredResourceInNestedInDirJar
        ManifestResource jarManifest
        ManifestResource nestedJarManifest
        TestResource jarManifestProperties
        ManifestResource manifestInDirectory
        PropertiesResource propertiesFileInDir
        PropertiesResource propertiesFileInJar
        PropertiesResource propertiesFileInNestedJar
        PropertiesResource propertiesFileInNestedInDirJar
        TestFile libraryJar
        TestFile nestedJar
        TestFile nestedInDirJar
        private TestFile libraryJarContents
        private TestFile nestedJarContents
        private TestFile nestedInDirJarContents
        private final String projectName
        final TestFile buildFile
        final TestFile settingsFile

        ProjectWithRuntimeClasspathNormalization(String projectName = null, Api api) {
            this.projectName = projectName
            this.root = projectName ? file(projectName) : temporaryFolder.testDirectory
            this.buildCacheDir = testDirectory.file("build-cache")

            def buildCachePath = TextUtil.normaliseFileSeparators(buildCacheDir.absolutePath)

            settingsFile = root.file('settings.gradle') << """
                buildCache {
                    local {
                        directory = file('${buildCachePath}')
                    }
                }
            """

            buildFile = root.file('build.gradle') << """
                apply plugin: 'base'
            """

            buildFile << declareCustomTask(api)

            nestedInDirJarContents = root.file('nestedInDirJarContents').create {
                ignoredResourceInNestedInDirJar = new TestResource(file('another/package/ignored.txt') << "This should be ignored", this.&createNestedInDirJar)
                notIgnoredResourceInNestedInDirJar = new TestResource(file('another/package/not-ignored.txt') << "This should not be ignored", this.&createNestedInDirJar)
                propertiesFileInNestedInDirJar = new PropertiesResource(file('some/path/to/foo.properties'), [(IGNORE_ME): 'this should be ignored', (DONT_IGNORE_ME): 'this should not be ignored'], this.&createNestedInDirJar)
            }
            root.file('classpath/dirEntry').create {
                ignoredResourceInDirectory = new TestResource(file("ignored.txt") << "This should be ignored")
                notIgnoredResourceInDirectory = new TestResource(file("not-ignored.txt") << "This should not be ignored")
                nestedInDirJar = file('nestedInDir.jar')
                propertiesFileInDir = new PropertiesResource(file('some/path/to/foo.properties'), [(IGNORE_ME): 'this should be ignored', (DONT_IGNORE_ME): 'this should not be ignored'])
                manifestInDirectory = new ManifestResource(file('META-INF/MANIFEST.MF')).withAttributes((IMPLEMENTATION_VERSION): "1.0.0")
            }
            nestedJarContents = root.file('nestedLibraryContents').create {
                nestedJarManifest = new ManifestResource(file('META-INF/MANIFEST.MF'), this.&createJar).withAttributes((IMPLEMENTATION_VERSION): "1.0.0")
                ignoredResourceInNestedJar = new TestResource(file('some/package/ignored.txt') << "This should be ignored", this.&createJar)
                notIgnoredResourceInNestedJar = new TestResource(file('some/package/not-ignored.txt') << "This should not be ignored", this.&createJar)
                propertiesFileInNestedJar = new PropertiesResource(file('some/path/to/foo.properties'), [(IGNORE_ME): 'this should be ignored', (DONT_IGNORE_ME): 'this should not be ignored'], this.&createJar)
            }
            libraryJarContents = root.file('libraryContents').create {
                jarManifest = new ManifestResource(file('META-INF/MANIFEST.MF'), this.&createJar).withAttributes((IMPLEMENTATION_VERSION): "1.0.0")
                jarManifestProperties = new TestResource(file('META-INF/build-info.properties') << "implementation-version=1.0.0", this.&createJar)
                ignoredResourceInJar = new TestResource(file('some/package/ignored.txt') << "This should be ignored", this.&createJar)
                notIgnoredResourceInJar = new TestResource(file('some/package/not-ignored.txt') << "This should not be ignored", this.&createJar)
                nestedJar = file('nested.jar')
                propertiesFileInJar = new PropertiesResource(file('some/path/to/foo.properties'), [(IGNORE_ME): 'this should be ignored', (DONT_IGNORE_ME): 'this should not be ignored'], this.&createJar)
            }
            libraryJar = root.file('library.jar')
            createJar()
            createNestedInDirJar()
        }

        String declareCustomTask(Api api) {
            if (api == Api.RUNTIME) {
                return """
                    task customTask {
                        def outputFile = file("\$temporaryDir/output.txt")
                        inputs.files("classpath/dirEntry", "library.jar")
                            .withPropertyName("classpath")
                            .withNormalizer(ClasspathNormalizer)
                        outputs.file(outputFile)
                            .withPropertyName("outputFile")
                        outputs.cacheIf { true }

                        doLast {
                            outputFile.text = "done"
                        }
                    }
                """
            } else {
                return """
                    @CacheableTask
                    class CustomTask extends DefaultTask {
                        @OutputFile File outputFile = new File(temporaryDir, "output.txt")
                        @Classpath FileCollection classpath = project.layout.files("classpath/dirEntry", "library.jar")

                        @TaskAction void generate() {
                            outputFile.text = "done"
                        }
                    }

                    task customTask(type: CustomTask)
                """
            }
        }

        void createJar() {
            if (nestedJar.exists()) {
                nestedJar.delete()
            }
            nestedJarContents.zipTo(nestedJar)
            if (libraryJar.exists()) {
                libraryJar.delete()
            }
            libraryJarContents.zipTo(libraryJar)
        }

        void createNestedInDirJar() {
            if (nestedInDirJar.exists()) {
                nestedInDirJar.delete()
            }
            nestedInDirJarContents.zipTo(nestedInDirJar)
        }

        ProjectWithRuntimeClasspathNormalization withFilesIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        ignore "**/ignored.txt"
                    }
                }
            """.stripIndent()
            return this
        }

        ProjectWithRuntimeClasspathNormalization withAllMetaInfIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        metaInf {
                            ignoreCompletely()
                        }
                    }
                }
            """.stripIndent()
            return this
        }

        ProjectWithRuntimeClasspathNormalization withManifestIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        metaInf {
                            ignoreManifest()
                        }
                    }
                }
            """.stripIndent()
            return this
        }

        ProjectWithRuntimeClasspathNormalization withManifestAttributesIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        metaInf {
                            ignoreAttribute "${IMPLEMENTATION_VERSION}"
                        }
                    }
                }
            """.stripIndent()
            return this
        }

        ProjectWithRuntimeClasspathNormalization withManifestPropertiesIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        metaInf {
                            ignoreProperty "implementation-version"
                        }
                    }
                }
            """.stripIndent()
            return this
        }

        ProjectWithRuntimeClasspathNormalization withPropertiesIgnored() {
            root.file('build.gradle') << """
                normalization {
                    runtimeClasspath {
                        properties {
                            ignoreProperty "${IGNORE_ME}"
                        }
                    }
                }
            """.stripIndent()
            return this
        }

        String getCustomTask() {
            return "${projectName ? ":${projectName}" : ''}:customTask"
        }
    }

    class TestResource {
        final TestFile backingFile
        private final Closure onChange

        TestResource(TestFile backingFile, Closure onChange = {}) {
            this.backingFile = backingFile
            this.onChange = onChange
        }

        void replaceContents(String contents) {
            backingFile.withWriter { w ->
                w << contents
            }
            changed()
        }

        void writeRaw(byte[]... content) {
            def stream = backingFile.newOutputStream()
            try {
                content.each { stream.write(it) }
            } finally {
                stream.close()
            }
            changed()
        }

        void changeContents() {
            backingFile << "More changes"
            changed()
        }

        void remove() {
            assert backingFile.delete()
            changed()
        }

        void add() {
            backingFile << "First creation of file"
            changed()
        }

        void changed() {
            onChange.call()
        }
    }

    class ManifestResource extends TestResource {
        Map<String, String> attributes

        ManifestResource(TestFile backingFile, Closure onChange = {}) {
            super(backingFile, onChange)
        }

        ManifestResource withAttributes(Map<String, String> attributes) {
            this.attributes = attributes
            def manifest = new Manifest()
            def mainAttributes = manifest.getMainAttributes()
            mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
            attributes.each {name, value ->
                mainAttributes.put(new Attributes.Name(name), value)
            }
            backingFile.withOutputStream {os ->
                manifest.write(os)
            }
            return this
        }

        ManifestResource changeAttributes(Map<String, String> attributes) {
            withAttributes(attributes)
            changed()
            return this
        }

        @Override
        void changeContents() {
            throw new UnsupportedOperationException()
        }

        @Override
        void add() {
            throw new UnsupportedOperationException()
        }
    }

    class PropertiesResource extends TestResource {
        String comment = ""

        PropertiesResource(TestFile backingFile, Map<String, String> initialProps, Closure finalizedBy={}) {
            super(backingFile, finalizedBy)
            withProperties() { Properties props ->
                props.putAll(initialProps)
            }
        }

        PropertiesResource changeProperty(String key, String value) {
            withProperties(true) { Properties props ->
                props.setProperty(key, value)
            }
            changed()
            return this
        }

        PropertiesResource setComment(String comment) {
            this.comment = comment
            return this
        }

        PropertiesResource setContent(String content) {
            // Preserve the order of the properties in the map when writing the properties file
            backingFile.withWriter {writer ->
                writer.write("# ${comment}\n")
                writer.write(content)
            }
            changed()
            return this
        }

        private withProperties(boolean loadFromExisting = false, Closure action) {
            Properties props = new Properties()
            if (loadFromExisting) {
                def inputStream = backingFile.newInputStream()
                try {
                    props.load(inputStream)
                } finally {
                    inputStream.close()
                }
            }
            action.call(props)
            def outputStream = backingFile.newOutputStream()
            try {
                props.store(outputStream, comment)
            } finally {
                outputStream.close()
            }
        }
    }
}
