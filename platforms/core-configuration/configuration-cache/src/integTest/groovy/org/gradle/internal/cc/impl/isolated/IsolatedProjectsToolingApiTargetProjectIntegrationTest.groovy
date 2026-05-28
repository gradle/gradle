/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import org.gradle.internal.cc.impl.fixtures.SomeToolingModel

class IsolatedProjectsToolingApiTargetProjectIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "model cache key differentiates between target subproject directories"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << """
            rootProject.name = 'root'
            include('a')
            include('b')
        """
        file("a/build.gradle") << "plugins.apply(my.MyPlugin)"
        file("b/build.gradle") << "plugins.apply(my.MyPlugin)"

        when:
        withIsolatedProjects()
        executer.inDirectory(file("a"))
        def modelA = fetchModel(SomeToolingModel)

        then:
        modelA.message == "It works from project :a"

        when:
        withIsolatedProjects()
        executer.inDirectory(file("b"))
        def modelB = fetchModel(SomeToolingModel)

        then:
        modelB.message == "It works from project :b"
    }
}
