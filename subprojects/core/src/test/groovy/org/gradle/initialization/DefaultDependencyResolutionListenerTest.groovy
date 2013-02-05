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

package org.gradle.initialization

import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.internal.project.ProjectInternal
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 2/5/13
 */
class DefaultDependencyResolutionListenerTest extends Specification {

    def accessListener = Mock(ProjectAccessListener)
    def listener = new DefaultDependencyResolutionListener(accessListener)

    def "fires events"() {
        def deps = Mock(ResolvableDependencies)
        def set = Mock(DependencySet)

        when:
        listener.beforeResolve(deps)

        then:
        1 * deps.getDependencies() >> set
        1 * set.all(_ as DefaultDependencyResolutionListener.BeforeResolve)
    }

    def "fires project access event for every project dependency"() {
        def project = Mock(ProjectInternal)
        def projectDependency = Mock(ProjectDependency)
        def externalDependency = Mock(ExternalDependency)
        def action = new DefaultDependencyResolutionListener.BeforeResolve(accessListener)

        when:
        action.execute(projectDependency)
        action.execute(externalDependency)

        then:
        1 * projectDependency.getDependencyProject() >> project
        1 * accessListener.beforeResolvingProjectDependency(project)
        0 * _._
    }
}
