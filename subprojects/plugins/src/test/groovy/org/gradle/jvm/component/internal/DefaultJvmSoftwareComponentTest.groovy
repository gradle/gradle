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

package org.gradle.jvm.component.internal


import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

/**
 * Tests {@link DefaultJvmSoftwareComponent}.
 */
class DefaultJvmSoftwareComponentTest extends AbstractProjectBuilderSpec {

    def "configures the project"() {
        when:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name", ext)

        then:
        component.sources == ext.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        component.runtimeClasspath == project.configurations.getByName(JvmConstants.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        component.runtimeElements == project.configurations.getByName(JvmConstants.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
        component.compileClasspath == project.configurations.getByName(JvmConstants.COMPILE_CLASSPATH_CONFIGURATION_NAME)
        project.configurations.getByName(JvmConstants.API_ELEMENTS_CONFIGURATION_NAME)
        project.configurations.getByName('mainSourceElements')
        project.configurations.getByName(JvmConstants.IMPLEMENTATION_CONFIGURATION_NAME)
        project.configurations.getByName(JvmConstants.RUNTIME_ONLY_CONFIGURATION_NAME)
        project.configurations.getByName(JvmConstants.COMPILE_ONLY_CONFIGURATION_NAME)
        project.configurations.getByName(JvmConstants.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
        project.tasks.getByName(JvmConstants.COMPILE_JAVA_TASK_NAME)
        project.tasks.getByName(JvmConstants.JAR_TASK_NAME)
        project.tasks.getByName(JvmConstants.JAVADOC_TASK_NAME)
        project.tasks.getByName(JvmConstants.PROCESS_RESOURCES_TASK_NAME)
    }

    def "has expected variants "() {
        when:
        project.plugins.apply(JavaBasePlugin)
        def ext = project.getExtensions().getByType(JavaPluginExtension.class)
        def component = project.objects.newInstance(DefaultJvmSoftwareComponent, "name", ext)

        then:
        component.usages*.name == ["apiElements", "runtimeElements"]
    }
}
