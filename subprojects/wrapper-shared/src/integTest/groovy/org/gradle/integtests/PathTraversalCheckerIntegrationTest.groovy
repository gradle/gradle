/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests

import org.gradle.api.internal.artifacts.transform.UnzipTransform
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Ensures Gradle core tasks and types are not subject to the
 * <a href="https://snyk.io/research/zip-slip-vulnerability">Zip Slip Vulnerability</a>.
 */
class PathTraversalCheckerIntegrationTest extends AbstractIntegrationSpec {

    private TestFile getEvilZip() {
        file("evil.zip")
    }

    def setup() {
        evilZip.withOutputStream {
            new ZipOutputStream(it).withCloseable { ZipOutputStream zos ->
                zos.putNextEntry(new ZipEntry('../../tmp/evil.sh'))
                zos.write("evil".getBytes('utf-8'))
                zos.closeEntry()
            }
        }
    }

    def "evil.zip has path traversal"() {
        given:
        def entryNames = new ZipFile(evilZip).withCloseable {
            it.entries().collect { it.name }
        }

        expect:
        entryNames == ['../../tmp/evil.sh']
    }

    def "Copy task refuses to unzip evil.zip"() {
        given:
        buildFile << '''
            task copyEvilZip(type: Copy) {
                from(zipTree('evil.zip'))
                into('.')
            }
        '''

        when:
        fails 'copyEvilZip'

        then:
        failureDescriptionContains "Execution failed for task ':copyEvilZip'"
        failure.assertHasErrorOutput "'../../tmp/evil.sh' is not a safe archive entry or path name"
    }

    def "UnzipTransform refuses to unzip evil.zip"() {
        given:
        buildFile """
            def artifactType = Attribute.of('artifactType', String)
            def compressed = Attribute.of('compressed', Boolean)

            dependencies {
                attributesSchema {
                    attribute(compressed)
                }
                artifactTypes {
                    zip {
                        attributes.attribute(compressed, true)
                    }
                }
            }

            abstract class TestUnzipTransform extends ${UnzipTransform.class.name} {
                @Override
                public void transform(TransformOutputs outputs) {
                    println "Executing unzip transform..."
                    super.transform(outputs)
                }
            }

            dependencies {
                registerTransform(TestUnzipTransform) {
                    from.attribute(artifactType, "zip").attribute(compressed, true)
                    to.attribute(artifactType, "directory").attribute(compressed, false)
                }
            }

            configurations {
                zipped {
                    attributes {
                        attribute(compressed, true)
                    }
                }
            }

            dependencies {
                zipped files('evil.zip')
            }

            println(
                configurations.zipped.incoming.artifactView {
                    attributes.attribute(compressed, false)
                }.artifacts.artifactFiles.files
            )
        """

        when:
        fails 'help'

        then:
        outputContains 'Executing unzip transform...'
        failure.assertHasErrorOutput "'../../tmp/evil.sh' is not a safe archive entry or path name"
    }
}
