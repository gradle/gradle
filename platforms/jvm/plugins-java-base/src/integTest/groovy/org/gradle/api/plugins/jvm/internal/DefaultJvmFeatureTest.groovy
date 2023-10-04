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

package org.gradle.api.plugins.jvm.internal

import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

// There aren't many tests for this class yet, but there will likely be more as we continue defining and refactoring the component/feature/target model
class DefaultJvmFeatureTest extends AbstractProjectBuilderSpec {
    def "can create multiple features in a project"() {
        given:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension)

        SourceSet one = ext.getSourceSets().create("one")
        SourceSet two = ext.getSourceSets().create("two")

        when:
        new DefaultJvmFeature("feature1", one, Collections.emptyList(), project, false, false)
        new DefaultJvmFeature("feature2", two, Collections.emptyList(), project, false, false)

        then:
        // TODO: There's no way to get the feature by name, so we'll check that some associated tasks and configurations are created
        project.tasks.getByName(one.getJarTaskName())
        project.tasks.getByName(two.getJarTaskName())
        project.configurations.getByName('oneImplementation')
        project.configurations.getByName('twoImplementation')
    }
}
