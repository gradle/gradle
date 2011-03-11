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
package org.gradle.plugins.eclipse.model.internal

import spock.lang.Specification

/**
 * @author Szczepan Faber, @date: 11.03.11
 */
class ProjectDependencyFactoryTest extends Specification {

    def ProjectDependencyFactory factory = new ProjectDependencyFactory()

    static class ProjectStub {
        String name
        EclipseProjectStub eclipseProject
    }

    static class EclipseProjectStub {
        String projectName
    }

    def "should create dependency using project name"() {
        given:
        def project = new ProjectStub(name: 'coolProject')

        when:
        def dependency = factory.create(project)

        then:
        dependency.path == '/coolProject'
    }

    def "should create dependency using eclipse projectName"() {
        given:
        def eclipseProject = new EclipseProjectStub(projectName: 'eclipse-project')
        def project = new ProjectStub(eclipseProject: eclipseProject)

        when:
        def dependency = factory.create(project)

        then:
        dependency.path == '/eclipse-project'
    }
}
