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

package org.gradle.plugins.ide.eclipse.internal

import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.Project
import spock.lang.Specification

/**
 * @author Szczepan Faber, @date: 20.03.11
 */
class EclipseDomainModelFactoryTest extends Specification {

    EclipseDomainModelFactory factory = new EclipseDomainModelFactory()

    static class ProjectStub {
        GeneratorTaskStub eclipseProject
        GeneratorTaskStub eclipseClasspath
    }

    static class GeneratorTaskStub {
        Object domainObject
    }

    def "throws meaningful exception when model cannot be created"() {
        when:
        factory.create(new ProjectStub())

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains 'plugin applied?'
    }

    def "creates model"() {
        given:
        def classpath = Mock(Classpath)
        def project = Mock(Project)

        when:
        def m = factory.create(new ProjectStub(
                eclipseProject: new GeneratorTaskStub( domainObject: project),
                eclipseClasspath: new GeneratorTaskStub( domainObject: classpath)
        ))

        then:
        m.classpath == classpath
        m.project == project
    }

    def "creates model even if classpath task not present on project"() {
        when:
        def m = factory.create(new ProjectStub(
                eclipseProject: new GeneratorTaskStub( domainObject: Mock(Project)),
                eclipseClasspath: null
        ))

        then:
        m.classpath != null
    }


}
