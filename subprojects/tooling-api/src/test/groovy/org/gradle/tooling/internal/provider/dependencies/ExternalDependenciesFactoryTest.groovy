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

import org.gradle.api.Project
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.Library
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import spock.lang.Specification

/**
 * @author Szczepan Faber, @date: 20.03.11
 */
class ExternalDependenciesFactoryTest extends Specification {

    def factory = new ExternalDependenciesFactory()

    def "creates instances"() {
        given:
        def project = Mock(Project)
        def somePathDir = new File('/projects/someLibrary')
        project.file('someLibrary') >> { somePathDir }
        def classpath = new Classpath()
        classpath.entries = [
                new SourceFolder('foo', '', [] as Set, '', [], []),
                new Library('someLibrary', true, '', [] as Set, '', '') ]

        when:
        def deps = factory.create(project, classpath)

        then:
        deps.size() == 1
        deps[0].file == somePathDir
    }
}
