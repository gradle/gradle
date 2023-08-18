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
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.util.internal.GUtil

import static org.gradle.plugin.devel.tasks.PluginUnderTestMetadata.IMPLEMENTATION_CLASSPATH_PROP_KEY
import static org.gradle.plugin.devel.tasks.PluginUnderTestMetadata.METADATA_FILE_NAME

class PluginUnderTestMetadataIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {

    private static final String TASK_NAME = 'pluginClasspathManifest'

    def setup() {
        buildFile << """
            apply plugin: 'java'
        """
        expectReindentedValidationMessage()
    }

    @ValidationTestFor(
        ValidationProblemId.VALUE_NOT_SET
    )
    def "fails the task for null plugin classpath and output directory"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()})
        """

        when:
        fails TASK_NAME

        then:
        failureDescriptionContains(missingNonConfigurableValueMessage { type(PluginUnderTestMetadata.name).property('outputDirectory').includeLink() })
    }

    def "implementation-classpath entry in metadata is empty if there is no classpath"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
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
                pluginClasspath.setFrom(sourceSets.main.runtimeClasspath)
                outputDirectory = file('build/some/other')
            }
        """

        when:
        succeeds TASK_NAME

        then:
        file("build/some/other/$METADATA_FILE_NAME").exists()
    }

    def "not up-to-date if pluginClasspath change"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
                pluginClasspath.setFrom(sourceSets.main.runtimeClasspath)
                outputDirectory = file('build/$TASK_NAME')
            }
        """
        file("src/main/java/Thing.java") << "class Thing { int foo; }"
        succeeds TASK_NAME

        when:
        // Move the source file to another location to change the plugin classpath
        // This should cause us to regenerate the script
        file("src/mainAlt/java/Thing.java") << "class Thing { int foo; }"
        buildFile << """
            sourceSets.create("mainAlt")

            ${TASK_NAME}.pluginClasspath.setFrom(sourceSets.mainAlt.runtimeClasspath)
        """
        succeeds TASK_NAME

        then:
        executedAndNotSkipped(":$TASK_NAME")

        and:
        succeeds TASK_NAME

        then:
        skipped(":$TASK_NAME")
    }
}
