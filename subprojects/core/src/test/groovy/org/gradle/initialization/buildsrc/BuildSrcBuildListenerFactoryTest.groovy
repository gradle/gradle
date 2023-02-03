/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.initialization.buildsrc

import org.gradle.api.Action
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.classpath.CachedClasspathTransformer
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Function

class BuildSrcBuildListenerFactoryTest extends Specification {

    def startParameter = Mock(StartParameterInternal)
    def projectState = Mock(ProjectState) {
        fromMutableState(_) >> { Function function -> function.apply(project) }
    }
    def project = Mock(ProjectInternal) {
        getOwner() >> projectState
    }
    def gradle = Mock(GradleInternal) {
        getStartParameter() >> startParameter
        getRootProject() >> project
    }

    def "executes buildSrc configuration action after projects are loaded"() {
        def action = Mock(Action)
        def listener = new BuildSrcBuildListenerFactory(action, TestUtil.objectInstantiator(), Stub(CachedClasspathTransformer)).create()

        when:
        listener.projectsLoaded(gradle)

        then:
        1 * action.execute(project)
    }
}
