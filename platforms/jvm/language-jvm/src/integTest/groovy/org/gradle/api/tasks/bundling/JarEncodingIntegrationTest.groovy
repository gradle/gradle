/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import spock.lang.Issue

import java.util.jar.JarFile
import java.util.jar.Manifest

@TestReproducibleArchives
@DoesNotSupportNonAsciiPaths(reason = "Tests manage their own encoding settings")
class JarEncodingIntegrationTest extends AbstractIntegrationSpec {
    // Only works on Java 8, see https://bugs.openjdk.java.net/browse/JDK-7050570
    @Issue(['GRADLE-1506'])
    def "create Jar with metadata encoded using UTF-8 when platform default charset is not UTF-8"() {
        given:
        buildScript """
            task jar(type: Jar) {
                from file('test')
                destinationDirectory = file('dest')
                archiveFileName = 'test.jar'
            }
        """.stripIndent()

        createDir('test') {
            // Use an UTF-8 caution symbol in file name
            // that will create a mojibake if encoded using another charset
            file 'mojibake☡.txt'
        }

        when:
        executer.withDefaultCharacterEncoding('windows-1252').withTasks("jar")
        executer.run()

        then:
        def jar = new JarTestFixture(file('dest/test.jar'))
        jar.assertContainsFile('mojibake☡.txt')
    }

    @Issue('GRADLE-1506')
    def "create Jar with metadata encoded using user supplied charset"() {
        given:
        buildScript """
            task jar(type: Jar) {
                metadataCharset = 'ISO-8859-15'
                from file('test')
                destinationDirectory = file('dest')
                archiveFileName = 'test.jar'
            }
        """.stripIndent()

        createDir('test') {
            file 'mojibak€.txt'
        }
        assert new String('mojibak€.txt').getBytes('ISO-8859-15') != new String('mojibak€.txt').getBytes('UTF-8')

        when:
        succeeds 'jar'

        then:
        def jar = new JarTestFixture(file('dest/test.jar'), 'ISO-8859-15')
        jar.assertContainsFile('mojibak€.txt')
    }

    @Issue('GRADLE-3374')
    def "write manifest encoded using UTF-8 when platform default charset is not UTF-8"() {
        given:
        buildScript """
            task jar(type: Jar) {
                from file('test')
                destinationDirectory = file('dest')
                archiveFileName = 'test.jar'
                manifest {
                    // Use an UTF-8 caution symbol in manifest entry
                    // that will create a mojibake if encoded using another charset
                    attributes 'moji': 'bake☡'
                }
            }
        """.stripIndent()

        when:
        executer.withDefaultCharacterEncoding('windows-1252').withTasks('jar')
        executer.run()

        then:
        def manifest = new JarTestFixture(file('dest/test.jar'), 'UTF-8', 'UTF-8').content('META-INF/MANIFEST.MF')
        manifest.contains('moji: bake☡')
    }

    @Issue("GRADLE-3374")
    def "merge manifest read using UTF-8 by default"() {
        given:
        buildScript """
            task jar(type: Jar) {
                from file('test')
                destinationDirectory = file('dest')
                archiveFileName = 'test.jar'
                manifest {
                    from('manifest-UTF-8.txt')
                }
            }
        """.stripIndent()
        file('manifest-UTF-8.txt').setText('moji: bak€', 'UTF-8')

        when:
        executer.withDefaultCharacterEncoding('ISO-8859-15').withTasks('jar')
        executer.run()

        then:
        def jar = new JarTestFixture(file('dest/test.jar'), 'UTF-8', 'UTF-8')
        def manifest = jar.content('META-INF/MANIFEST.MF')
        manifest.contains('moji: bak€')
    }

    @ToBeFixedForConfigurationCache
    @Issue('GRADLE-3374')
    def "write manifests using a user defined character set"() {
        given:
        buildScript """
            task jar(type: Jar) {
                from file('test')
                destinationDirectory = file('dest')
                archiveFileName = 'test.jar'
                manifestContentCharset = 'ISO-8859-15'
                manifest {
                    attributes 'moji': 'bak€'
                }
            }
        """.stripIndent()

        when:
        executer.withDefaultCharacterEncoding('UTF-8').withTasks('jar')
        executer.run()

        then:
        def jar = new JarTestFixture(file('dest/test.jar'), 'UTF-8', 'ISO-8859-15')
        def manifest = jar.content('META-INF/MANIFEST.MF')
        manifest.contains('moji: bak€')
    }

    @Issue('GRADLE-3374')
    def "merge manifests using user defined character sets"() {
        given:
        buildScript """
            task jar(type: Jar) {
                from file('test')
                destinationDirectory = file('dest')
                archiveFileName = 'test.jar'
                manifest {
                    attributes 'moji': 'bak€'
                    from('manifest-ISO-8859-15.txt') {
                        // Charset used to decode the read manifest content
                        contentCharset = 'ISO-8859-15'
                    }
                }
            }
        """.stripIndent()
        file('manifest-ISO-8859-15.txt').setText('bake: moji€', 'ISO-8859-15')

        when:
        executer.withDefaultCharacterEncoding('windows-1252').withTasks('jar')
        executer.run()

        then:
        def jar = new JarTestFixture(file('dest/test.jar'), 'UTF-8', 'UTF-8')
        def manifest = jar.content('META-INF/MANIFEST.MF')
        manifest.contains('moji: bak€')
        manifest.contains('bake: moji€')
    }

    @Issue('GRADLE-3374')
    def "can merge manifests containing split multi-byte chars using #taskType task"() {
        // Note that there's no need to cover this case with merge read charsets
        // other than UTF-8 because it's not supported by the JVM.
        given:
        def attributeNameMerged = 'Looong-Name-Of-Manifest-Entry'
        def attributeNameWritten = 'Another-Looooooong-Name-Entry'
        // Means 'long russian text'
        def attributeValue = 'com.acme.example.pack.**, длинный.текст.на.русском.языке.**'

        def mergedManifestFilename = 'manifest-with-split-multi-byte-char.txt'
        def mergedManifest = new Manifest()
        mergedManifest.mainAttributes.putValue('Manifest-Version', '1.0')
        mergedManifest.mainAttributes.putValue(attributeNameMerged, attributeValue)
        def mergedManifestFile = file(mergedManifestFilename)
        mergedManifestFile.withOutputStream { mergedManifest.write(it) }

        buildScript """
            $taskTypeDeclaration
            task jar(type: $taskType) {
                from file('test')
                destinationDirectory = file('dest')
                archiveFileName = 'test.jar'
                manifest {
                    attributes '$attributeNameWritten': '$attributeValue'
                    from file('$mergedManifestFilename')
                }
            }
        """.stripIndent()

        when:
        executer.withDefaultCharacterEncoding('windows-1252').withTasks('jar')
        executer.withArgument("--stacktrace")
        executer.run()

        then:
        def jar = new JarFile(file('dest/test.jar'))
        try {
            def manifest = jar.manifest
            assert manifest.mainAttributes.getValue(attributeNameWritten) == attributeValue
            assert manifest.mainAttributes.getValue(attributeNameMerged) == attributeValue
        } finally {
            jar.close()
        }

        where:
        taskType            | taskTypeDeclaration
        'Jar'               | ''
        'CustomJarManifest' | customJarManifestTask()
    }

    @Issue('GRADLE-3374')
    def "reports error for unsupported manifest content charsets, write #writeCharset, read #readCharset"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildScript """
            task jar(type: Jar) {
                from file('test')
                destinationDirectory = file('dest')
                archiveFileName = 'test.jar'
                manifestContentCharset = $writeCharset
                manifest {
                    from('manifest-to-merge.txt') {
                        contentCharset = $readCharset
                    }
                }
            }
        """.stripIndent()

        when:
        executer.withDefaultCharacterEncoding('UTF-8')
        fails 'jar'

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause(cause)

        where:
        writeCharset    | readCharset     | cause
        "'UNSUPPORTED'" | "'UTF-8'"       | "Charset for manifestContentCharset 'UNSUPPORTED' is not supported by your JVM"
        "'UTF-8'"       | "'UNSUPPORTED'" | "Charset for contentCharset 'UNSUPPORTED' is not supported by your JVM"
        null            | "'UTF-8'"       | "manifestContentCharset must not be null"
        "'UTF-8'"       | null            | "contentCharset must not be null"
    }

    private static String customJarManifestTask() {
        return '''
            class CustomJarManifest extends org.gradle.jvm.tasks.Jar {
                CustomJarManifest() {
                    super();
                    setManifest(new CustomManifest(getFileResolver()))
                }
            }

            class CustomManifest implements org.gradle.api.java.archives.Manifest {
                @Delegate org.gradle.api.java.archives.Manifest delegate

                CustomManifest(fileResolver) {
                    this.delegate = new org.gradle.api.java.archives.internal.DefaultManifest(fileResolver)
                }
            }
        '''.stripIndent()
    }
}
