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

import spock.lang.Specification
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.Convention
import org.gradle.api.internal.plugins.EmbeddableJavaProject
import org.gradle.StartParameter

class BuildSrcBuildListenerFactoryTest extends Specification {

    def startParameter = Mock(StartParameter)
    def plugin = Stub(EmbeddableJavaProject)
    def convention = Mock(Convention) {
        getPlugin(EmbeddableJavaProject) >> plugin
    }
    def project = Mock(ProjectInternal) {
        getConvention() >> convention
    }
    def gradle = Mock(GradleInternal) {
        getStartParameter() >> startParameter
        getRootProject() >> project
    }

    def "configures task names when rebuild on"() {
        def listener = new BuildSrcBuildListenerFactory().create(true)
        plugin.getRebuildTasks() >> ['fooBuild']

        when:
        listener.onConfigure(gradle)

        then:
        1 * startParameter.setTaskNames(['fooBuild'])
    }

    def "configures task names when rebuild off"() {
        def listener = new BuildSrcBuildListenerFactory().create(false)
        plugin.getBuildTasks() >> ['barBuild']

        when:
        listener.onConfigure(gradle)

        then:
        1 * startParameter.setTaskNames(['barBuild'])
    }
}
