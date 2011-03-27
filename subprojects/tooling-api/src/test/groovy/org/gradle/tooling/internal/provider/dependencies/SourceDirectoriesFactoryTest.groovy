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
import org.gradle.plugins.ide.eclipse.model.Container
import org.gradle.plugins.ide.eclipse.model.SourceFolder
import spock.lang.Specification

/**
 * @author Szczepan Faber, @date: 20.03.11
 */
class SourceDirectoriesFactoryTest extends Specification {

    def factory = new SourceDirectoriesFactory()

    def "creates instances"() {
        given:
        def project = Mock(Project)
        def somePathDir = new File('/projects/somePath')
        project.file('somePath') >> { somePathDir }
        def classpath = new Classpath()
        classpath.entries = [
                new SourceFolder('somePath', '', [] as Set, '', [], []),
                new Container('foo', true, '', [] as Set) ]

        when:
        def dirs = factory.create(project, classpath)

        then:
        dirs.size() == 1
        dirs[0].path == 'somePath'
        dirs[0].directory == somePathDir
    }
}
