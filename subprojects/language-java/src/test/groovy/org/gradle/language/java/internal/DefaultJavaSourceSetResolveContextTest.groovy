/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java.internal

import org.gradle.api.Project
import spock.lang.Specification
import spock.lang.Unroll

class DefaultJavaSourceSetResolveContextTest extends Specification {
    def "resolve context can be created from a java source set"() {
        given:
        def project = Mock(Project)
        def sourceset = Mock(DefaultJavaLanguageSourceSet)

        when:
        def context = new DefaultJavaSourceSetResolveContext(project, sourceset)

        then:
        context.project == project
        context.dependencies.empty
        context.allDependencies.empty
    }

    @Unroll
    def "context name for project #path and library #library is #contextName"() {
        given:
        def project = Mock(Project)
        def sourceset = Mock(DefaultJavaLanguageSourceSet)

        when:
        project.path >> path
        sourceset.parentName >> library
        def context = new DefaultJavaSourceSetResolveContext(project, sourceset)

        then:
        context.name == contextName

        where:
        path       | library  | contextName
        ':myPath'  | 'myLib'  | 'project :myPath library myLib'
        ':myPath'  | 'myLib2' | 'project :myPath library myLib2'
        ':myPath2' | 'myLib'  | 'project :myPath2 library myLib'
    }
}
