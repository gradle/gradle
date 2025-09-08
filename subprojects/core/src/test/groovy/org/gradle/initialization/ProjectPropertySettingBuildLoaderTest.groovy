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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.plugins.ExtraPropertiesExtensionInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.properties.GradlePropertiesController
import org.gradle.initialization.properties.DefaultGradleProperties
import spock.lang.Specification

class ProjectPropertySettingBuildLoaderTest extends Specification {

    final GradleInternal gradle = Mock()
    final SettingsInternal settings = Mock()
    final ProjectInternal rootProject = Mock()
    final ProjectInternal childProject = Mock()
    final GradlePropertiesController gradlePropertiesController = Mock()
    final ProjectPropertySettingBuildLoader loader = new ProjectPropertySettingBuildLoader(gradlePropertiesController, Mock(BuildLoader))
    final ExtraPropertiesExtensionInternal rootExtraProperties = Mock()
    final ExtraPropertiesExtensionInternal childExtraProperties = Mock()

    def setup() {
        _ * gradle.rootProject >> rootProject
        _ * rootProject.childProjectsUnchecked >> [child: childProject]
        _ * childProject.childProjectsUnchecked >> [:]
        _ * rootProject.extensions >> Mock(ExtensionContainerInternal) {
            extraProperties >> { rootExtraProperties }
        }
        _ * childProject.extensions >> Mock(ExtensionContainerInternal) {
            extraProperties >> { childExtraProperties }
        }
    }

    def "loading mutates projects in the hierarchy and installs gradle properties as their extra properties"() {
        given:
        1 * gradlePropertiesController.loadGradleProperties(rootProject.projectIdentity, rootProject.projectDir)
        1 * gradlePropertiesController.loadGradleProperties(childProject.projectIdentity, childProject.projectDir)
        1 * gradlePropertiesController.getGradleProperties(rootProject.projectIdentity) >> gradleProperties([
            "": 'ignored', // empty properties are ignored

            // select properties result in direct calls to setter-methods on Project
            description: 'my project',
            group: 'my-group',
            version: '1.0',
            status: 'my-status',
            buildDir: 'my-build-dir',

            // any other properties contribute to extra-properties
            foo: 'bar',
        ])
        1 * gradlePropertiesController.getGradleProperties(childProject.projectIdentity) >> gradleProperties([
            childProp: 'child'
        ])

        when:
        loader.load(settings, gradle)

        then:
        1 * rootProject.setDescription('my project')
        1 * rootProject.setGroup('my-group')
        1 * rootProject.setVersion('1.0')
        1 * rootProject.setStatus('my-status')
        1 * rootProject.setBuildDir('my-build-dir')

        and:
        1 * rootExtraProperties.setGradleProperties(_) >> { GradleProperties props ->
            assert props.properties == [foo: "bar"]
        }
        1 * childExtraProperties.setGradleProperties(_) >> { GradleProperties props ->
            assert props.properties == [childProp: "child"]
        }
    }

    def "select properties are looked up unconditionally during loading because they might mutate the project state"() {
        given:
        def rootGradleProperties = Mock(GradleProperties)
        def childGradleProperties = Mock(GradleProperties)
        1 * gradlePropertiesController.loadGradleProperties(rootProject.projectIdentity, rootProject.projectDir)
        1 * gradlePropertiesController.loadGradleProperties(childProject.projectIdentity, childProject.projectDir)
        1 * gradlePropertiesController.getGradleProperties(rootProject.projectIdentity) >> rootGradleProperties
        1 * gradlePropertiesController.getGradleProperties(childProject.projectIdentity) >> childGradleProperties

        when:
        loader.load(settings, gradle)

        then:
        1 * rootGradleProperties.findUnsafe("version") >> null
        1 * rootGradleProperties.findUnsafe("group") >> null
        1 * rootGradleProperties.findUnsafe("status") >> null
        1 * rootGradleProperties.findUnsafe("buildDir") >> null
        1 * rootGradleProperties.findUnsafe("description") >> null
        0 * rootGradleProperties._

        0 * rootProject.setDescription(_)
        0 * rootProject.setGroup(_)
        0 * rootProject.setVersion(_)
        0 * rootProject.setStatus(_)
        0 * rootProject.setBuildDir(_)

        then:
        1 * childGradleProperties.findUnsafe("version") >> null
        1 * childGradleProperties.findUnsafe("group") >> null
        1 * childGradleProperties.findUnsafe("status") >> null
        1 * childGradleProperties.findUnsafe("buildDir") >> null
        1 * childGradleProperties.findUnsafe("description") >> null
        0 * childGradleProperties._

        0 * childProject.setDescription(_)
        0 * childProject.setGroup(_)
        0 * childProject.setVersion(_)
        0 * childProject.setStatus(_)
        0 * childProject.setBuildDir(_)
    }

    private static GradleProperties gradleProperties(Map<String, String> props) {
        new DefaultGradleProperties(props)
    }
}
