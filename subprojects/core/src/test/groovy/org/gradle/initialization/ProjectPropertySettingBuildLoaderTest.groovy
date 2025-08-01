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

import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.plugins.ExtraPropertiesExtensionInternal
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
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
    final GradlePropertiesController gradlePropertiesController = Mock()
    final GradleProperties rootGradleProperties = Mock()
    final GradleProperties childGradleProperties = Mock()
    final ProjectIdentity rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
    final ProjectIdentity childProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.path(":child"), "child")
    final File rootProjectDir = tmpDir.createDir('root')
    final File childProjectDir = tmpDir.createDir('child')
    final ProjectPropertySettingBuildLoader loader = new ProjectPropertySettingBuildLoader(gradlePropertiesController, target)
    final ExtensionContainerInternal rootExtension = Mock()
    final ExtraPropertiesExtensionInternal rootProperties = Mock()
    final ExtensionContainerInternal childExtension = Mock()
    final ExtraPropertiesExtensionInternal childProperties = Mock()

    def setup() {
        _ * gradle.rootProject >> rootProject
        _ * rootProject.childProjectsUnchecked >> [child: childProject]
        _ * childProject.childProjectsUnchecked >> [:]
        _ * rootProject.projectDir >> rootProjectDir
        _ * childProject.projectDir >> childProjectDir
        _ * rootProject.projectIdentity >> rootProjectIdentity
        _ * childProject.projectIdentity >> childProjectIdentity
        _ * rootProject.extensions >> rootExtension
        _ * childProject.extensions >> childExtension
        _ * rootExtension.extraProperties >> rootProperties
        _ * childExtension.extraProperties >> childProperties
    }

    def "delegates to build loader"() {
        given:
        _ * gradlePropertiesController.loadGradleProperties(_, _)
        _ * gradlePropertiesController.getGradleProperties(rootProjectIdentity) >> rootGradleProperties
        _ * gradlePropertiesController.getGradleProperties(childProjectIdentity) >> childGradleProperties
        _ * rootGradleProperties.getProperties() >> [:]
        _ * childGradleProperties.getProperties() >> [:]

        when:
        loader.load(settings, gradle)

        then:
        1 * target.load(settings, gradle)
        0 * target._
    }

    def "sets project properties on each project in hierarchy"() {
        given:
        1 * gradlePropertiesController.loadGradleProperties(rootProjectIdentity, rootProjectDir)
        1 * gradlePropertiesController.loadGradleProperties(childProjectIdentity, childProjectDir)
        1 * gradlePropertiesController.getGradleProperties(rootProjectIdentity) >> rootGradleProperties
        1 * gradlePropertiesController.getGradleProperties(childProjectIdentity) >> childGradleProperties
        1 * rootGradleProperties.getProperties() >> [prop: 'value']
        1 * childGradleProperties.getProperties() >> [prop: 'value']

        when:
        loader.load(settings, gradle)

        then:
        1 * rootProperties.setGradleProperties(ImmutableMap.of('prop', 'value'))
        1 * childProperties.setGradleProperties(ImmutableMap.of('prop', 'value'))
    }

    def "defines extra property for unknown property"() {
        given:
        1 * gradlePropertiesController.loadGradleProperties(rootProjectIdentity, rootProjectDir)
        1 * gradlePropertiesController.loadGradleProperties(childProjectIdentity, childProjectDir)
        1 * gradlePropertiesController.getGradleProperties(rootProjectIdentity) >> rootGradleProperties
        1 * gradlePropertiesController.getGradleProperties(childProjectIdentity) >> childGradleProperties
        1 * rootGradleProperties.getProperties() >> [prop: 'value']
        1 * childGradleProperties.getProperties() >> [:]

        when:
        loader.load(settings, gradle)

        then:
        1 * rootProperties.setGradleProperties(ImmutableMap.of('prop', 'value'))
    }

    def "loads project properties from gradle.properties file in project dir"() {
        given:
        1 * gradlePropertiesController.loadGradleProperties(rootProjectIdentity, rootProjectDir)
        1 * gradlePropertiesController.loadGradleProperties(childProjectIdentity, childProjectDir)
        1 * gradlePropertiesController.getGradleProperties(rootProjectIdentity) >> rootGradleProperties
        1 * gradlePropertiesController.getGradleProperties(childProjectIdentity) >> childGradleProperties
        1 * rootGradleProperties.getProperties() >> [prop: 'rootValue']
        1 * childGradleProperties.getProperties() >> [prop: 'childValue']

        when:
        loader.load(settings, gradle)

        then:
        1 * rootProperties.setGradleProperties(ImmutableMap.of('prop', 'rootValue'))
        1 * childProperties.setGradleProperties(ImmutableMap.of('prop', 'childValue'))
    }

    def "defines project properties from Project class"() {
        given:
        1 * gradlePropertiesController.loadGradleProperties(rootProjectIdentity, rootProjectDir)
        1 * gradlePropertiesController.loadGradleProperties(childProjectIdentity, childProjectDir)
        1 * gradlePropertiesController.getGradleProperties(rootProjectIdentity) >> rootGradleProperties
        1 * gradlePropertiesController.getGradleProperties(childProjectIdentity) >> childGradleProperties
        1 * rootGradleProperties.getProperties() >> [
            description: 'my project',
            group: 'my-group',
            version: '1.0',
            status: 'my-status',
            buildDir: 'my-build-dir',
            foo: 'bar', // unknown properties are ignored
            "": 'ignored' // empty properties are ignored
        ]
        1 * childGradleProperties.getProperties() >> [
            description: 'my project',
            group: 'my-group',
            version: '1.0',
            status: 'my-status',
            buildDir: 'my-build-dir',
            foo: 'bar', // unknown properties are ignored
            "": 'ignored' // empty properties are ignored
        ]

        when:
        loader.load(settings, gradle)

        then:
        1 * rootProject.setDescription('my project')
        1 * rootProject.setGroup('my-group')
        1 * rootProject.setVersion('1.0')
        1 * rootProject.setStatus('my-status')
        1 * rootProject.setBuildDir('my-build-dir')

        1 * childProject.setDescription('my project')
        1 * childProject.setGroup('my-group')
        1 * childProject.setVersion('1.0')
        1 * childProject.setStatus('my-status')
        1 * childProject.setBuildDir('my-build-dir')

        1 * rootProperties.setGradleProperties(ImmutableMap.of('foo', 'bar'))
        1 * childProperties.setGradleProperties(ImmutableMap.of('foo', 'bar'))
    }

}
