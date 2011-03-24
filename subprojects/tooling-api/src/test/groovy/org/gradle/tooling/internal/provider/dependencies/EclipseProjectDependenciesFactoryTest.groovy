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

package org.gradle.tooling.internal.provider.dependencies

import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.ProjectDependency
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3
import spock.lang.Specification

/**
 * @author Szczepan Faber, @date: 20.03.11
 */
class EclipseProjectDependenciesFactoryTest extends Specification {

    def factory = new EclipseProjectDependenciesFactory()

    def "creates instances"() {
        given:
        def projectAInstance = Mock(EclipseProjectVersion3)
        def projectMapping = [':projectA' : projectAInstance]
        def classpath = new Classpath()
        classpath.entries = [
                new SourceFolder('foo', '', [] as Set, '', [], []),
                new ProjectDependency('/projectA', true, '', [] as Set, ':projectA'),
                new ProjectDependency('/projectB', true, '', [] as Set, ':projectB') ]

        when:
        def deps = factory.create(projectMapping, classpath)

        then:
        deps.size() == 2
        deps[0].path == 'projectA'
        deps[0].targetProject == projectAInstance
        deps[1].path == 'projectB'
        deps[1].targetProject == null
    }
}
