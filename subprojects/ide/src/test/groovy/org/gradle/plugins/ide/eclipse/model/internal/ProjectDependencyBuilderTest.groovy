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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.api.Project
import org.gradle.util.HelperUtil
import spock.lang.Specification

class ProjectDependencyBuilderTest extends Specification {

    def Project project = HelperUtil.createRootProject()
    def ProjectDependencyBuilder builder = new ProjectDependencyBuilder()

    def "should create dependency using project name"() {
        when:
        def dependency = builder.build(project, 'compile')

        then:
        dependency.path == "/$project.name"
        dependency.declaredConfigurationName == 'compile'
    }

    def "should create dependency using eclipse projectName"() {
        given:
        project.apply(plugin: 'eclipse')
        project.eclipse.project.name = 'foo'

        when:
        def dependency = builder.build(project, 'runtime')

        then:
        dependency.path == '/foo'
    }
}
