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

package org.gradle.plugin.devel.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GUtil

import static org.gradle.plugin.devel.tasks.PluginUnderTestMetadata.*

class PluginUnderTestMetadataIntegrationTest extends AbstractIntegrationSpec {

    private static final String TASK_NAME = 'pluginClasspathManifest'

    def setup() {
        buildFile << """
            apply plugin: 'java'
        """
    }

    def "fails the task for null plugin classpath and output directory"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()})
        """

        when:
        fails TASK_NAME

        then:
        failure.assertHasCause("No value has been specified for property 'pluginClasspath'.")
        failure.assertHasCause("No value has been specified for property 'outputDirectory'.")
    }

    def "implementation-classpath entry in metadata is empty if there is no classpath"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
                pluginClasspath = files()
                outputDirectory = file('build/$TASK_NAME')
            }
        """

        when:
        succeeds TASK_NAME

        then:
        def metadataFile = file("build/$TASK_NAME/$METADATA_FILE_NAME")
        metadataFile.exists() && metadataFile.isFile()
        !GUtil.loadProperties(metadataFile).containsKey(IMPLEMENTATION_CLASSPATH_PROP_KEY)
    }

    def "can reconfigure output directory for metadata file"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
                pluginClasspath = sourceSets.main.runtimeClasspath
                outputDirectory = file('build/some/other')
            }
        """

        when:
        succeeds TASK_NAME

        then:
        file("build/some/other/$METADATA_FILE_NAME").exists()
    }

    def "hash changes when plugin classpath changes"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
                pluginClasspath = sourceSets.main.runtimeClasspath
                outputDirectory = file('build/$TASK_NAME')
            }
        """
        def sourceFile = file("src/main/java/Thing.java") << "class Thing { int foo; }"
        def metadataFile = file("build/$TASK_NAME/$METADATA_FILE_NAME")

        when:
        succeeds TASK_NAME
        def hash1 = GUtil.loadProperties(metadataFile).getProperty(IMPLEMENTATION_CLASSPATH_HASH_PROP_KEY)

        sourceFile.text = "class Thing { int foofoo; }"
        succeeds TASK_NAME

        then:
        executedAndNotSkipped(":$TASK_NAME")
        def hash2 = GUtil.loadProperties(metadataFile).getProperty(IMPLEMENTATION_CLASSPATH_HASH_PROP_KEY)
        hash2 != hash1

        when:
        sourceFile.text = "class Thing { int foofoo;                 }" // change source, but not class file
        succeeds TASK_NAME

        then:
        executedAndNotSkipped(":compileJava")
        skipped(":$TASK_NAME")

        when:
        metadataFile.delete()
        succeeds TASK_NAME

        then:
        executedAndNotSkipped(":$TASK_NAME")
        def hash3 = GUtil.loadProperties(metadataFile).getProperty(IMPLEMENTATION_CLASSPATH_HASH_PROP_KEY)
        hash3 == hash2
    }
}
