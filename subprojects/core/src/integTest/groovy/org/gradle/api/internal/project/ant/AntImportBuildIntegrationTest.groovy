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

package org.gradle.api.internal.project.ant

import groovy.xml.MarkupBuilder
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class AntImportBuildIntegrationTest extends AbstractIntegrationSpec {

    private static final String EXECUTER_SYS_PROP = "org.gradle.integtest.executer"
    private static final String EXECUTER_CONFIG_CACHE = "configCache"
    private static final String EXECUTER_FORKING = "forking"

    private static String initialExecuterType

    File antBuildFile

    /**
     * Ensure that {@link org.gradle.integtests.fixtures.executer.ConfigurationCacheGradleExecuter}
     * will not be created, because of serialization issues with {@link org.gradle.api.tasks.ant.AntTarget}.
     */
    def setupSpec() {
        initialExecuterType = System.getProperty(EXECUTER_SYS_PROP)
        if (initialExecuterType == EXECUTER_CONFIG_CACHE) {
            System.setProperty(EXECUTER_SYS_PROP, EXECUTER_FORKING)
        }
    }

    /**
     * Restore initial property value.
     */
    def cleanupSpec() {
        if (initialExecuterType != null) {
            System.setProperty(EXECUTER_SYS_PROP, initialExecuterType)
        }
    }

    def setup() {
        antBuildFile = new File(testDirectory, 'build.xml')
        antBuildFile.withWriter { Writer writer ->
            def xml = new MarkupBuilder(writer)
            xml.project {
                target(name: 'test-build') {
                    echo(message: 'Basedir is: ${basedir}')
                }
            }
        }
    }

    private void "test basedir"(String buildFileContents, File expectedBasedir, String taskName) {
        // given
        buildFile << buildFileContents
        // when
        succeeds(taskName)
        // then
        outputContains("[ant:echo] Basedir is: " + expectedBasedir.getAbsolutePath())
    }

    def "by default basedir is same as Ant file location"() {
        expect:
        "test basedir"("""
            ant.importBuild 'build.xml'
        """, testDirectory, "test-build")
    }

    @Issue("gradle/gradle#1698")
    def "user can set different basedir for imported Ant file"() {
        expect:
        "test basedir"("""
            ant.importBuild('build.xml', '..')
        """, testDirectory.getParentFile(), "test-build")
    }

    def "user can specify transformer without specifying basedir"() {
        expect:
        "test basedir"("""
            ant.importBuild('build.xml') { antTaskName ->
                'ant-' + antTaskName
            }
        """, testDirectory, "ant-test-build")
    }

    @Issue("gradle/gradle#1698")
    def "user can specify both basedir and transformer"() {
        expect:
        "test basedir"("""
            ant.importBuild('build.xml', '..') { antTaskName ->
                'ant-' + antTaskName
            }
        """, testDirectory.getParentFile(), "ant-test-build")
    }

}
