/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.initialization

import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.GUtil
import org.junit.Rule
import spock.lang.Specification

class ProjectPropertySettingBuildLoaderTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    final BuildLoader target = Mock()
    final GradleInternal gradle = Mock()
    final SettingsInternal settings = Mock()
    final ProjectInternal rootProject = Mock()
    final ProjectInternal childProject = Mock()
    final GradleProperties gradleProperties = Mock()
    final File rootProjectDir = tmpDir.createDir('root')
    final File childProjectDir = tmpDir.createDir('child')
    final FileResourceListener fileResourceListener = Mock(FileResourceListener)
    final ProjectPropertySettingBuildLoader loader = new ProjectPropertySettingBuildLoader(gradleProperties, target, fileResourceListener)
    final ExtensionContainerInternal rootExtension = Mock()
    final ExtraPropertiesExtension rootProperties = Mock()
    final ExtensionContainerInternal childExtension = Mock()
    final ExtraPropertiesExtension childProperties = Mock()

    def setup() {
        _ * gradle.rootProject >> rootProject
        _ * rootProject.childProjectsUnchecked >> [child: childProject]
        _ * childProject.childProjectsUnchecked >> [:]
        _ * rootProject.projectDir >> rootProjectDir
        _ * childProject.projectDir >> childProjectDir
        _ * rootProject.extensions >> rootExtension
        _ * childProject.extensions >> childExtension
        _ * rootExtension.extraProperties >> rootProperties
        _ * childExtension.extraProperties >> childProperties
        1 * fileResourceListener.fileObserved(rootPropertiesFile())
        1 * fileResourceListener.fileObserved(childPropertiesFile())
    }

    def "delegates to build loader"() {
        given:
        _ * gradleProperties.mergeProperties(!null) >> [:]

        when:
        loader.load(settings, gradle)

        then:
        1 * target.load(settings, gradle)
        0 * target._
    }

    def "sets project properties on each project in hierarchy"() {
        given:
        2 * gradleProperties.mergeProperties([:]) >> [prop: 'value']

        when:
        loader.load(settings, gradle)

        then:
        1 * rootProperties.set('prop', 'value')
        1 * childProperties.set('prop', 'value')
    }

    def "defines extra property for unknown property"() {
        given:
        2 * gradleProperties.mergeProperties([:]) >> [prop: 'value']

        when:
        loader.load(settings, gradle)

        then:
        1 * rootProperties.set('prop', 'value')
    }

    def "loads project properties from gradle.properties file in project dir"() {
        given:
        GUtil.saveProperties(new Properties([prop: 'rootValue']), rootPropertiesFile())
        GUtil.saveProperties(new Properties([prop: 'childValue']), childPropertiesFile())

        when:
        loader.load(settings, gradle)

        then:
        1 * gradleProperties.mergeProperties([prop: 'rootValue']) >> [prop: 'rootValue']
        1 * gradleProperties.mergeProperties([prop: 'childValue']) >> [prop: 'childValue']
        1 * rootProperties.set('prop', 'rootValue')
        1 * childProperties.set('prop', 'childValue')
    }

    def "defines project properties from Project class"() {
        given:
        2 * gradleProperties.mergeProperties([:]) >> [version: '1.0']

        when:
        loader.load(settings, gradle)

        then:
        1 * rootProject.setVersion('1.0')
        1 * childProject.setVersion('1.0')
        0 * rootProperties.set(_, _)
        0 * childProperties.set(_, _)
    }

    private File childPropertiesFile() {
        new File(childProjectDir, Project.GRADLE_PROPERTIES)
    }

    private File rootPropertiesFile() {
        new File(rootProjectDir, Project.GRADLE_PROPERTIES)
    }
}
