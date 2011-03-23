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

package org.gradle.plugins.ide.idea.model.internal

import spock.lang.Specification

/**
 * @author Szczepan Faber, @date: 19.03.11
 */
class ModuleDependencyBuilderTest extends Specification {

    def builder = new ModuleDependencyBuilder()

    static class ProjectStub {
        String name
        IdeaModuleStub ideaModule
    }

    static class IdeaModuleStub { String moduleName }

    def "builds dependency for project"() {
        when:
        def dependency = builder.create(new ProjectStub(ideaModule: new IdeaModuleStub(moduleName: 'services')), 'compile')
        then:
        dependency.scope == 'compile'
        dependency.name == 'services'
    }

    def "builds dependency for nonIdea project"() {
        when:
        def dependency = builder.create(new ProjectStub(name: 'api'), 'compile')
        then:
        dependency.scope == 'compile'
        dependency.name == 'api'
    }
}