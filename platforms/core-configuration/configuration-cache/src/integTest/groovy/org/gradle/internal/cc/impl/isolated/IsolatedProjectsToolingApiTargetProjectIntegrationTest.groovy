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

    def "caches model per target project directory"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile """
            rootProject.name = 'root'
            include('a')
            include('b')
        """
        file("a/build.gradle") << "plugins.apply(my.MyPlugin)"
        file("b/build.gradle") << "plugins.apply(my.MyPlugin)"

        when: "first query targets subproject 'a' — stores a new cache entry"
        withIsolatedProjects()
        executer.inDirectory(file("a"))
        def modelA1 = fetchModel(SomeToolingModel)

        then:
        modelA1.message == "It works from project :a"
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":")
            modelsCreated(":a")
        }

        when: "query targets subproject 'b' — must miss :a's cache and store its own"
        withIsolatedProjects()
        executer.inDirectory(file("b"))
        def modelB1 = fetchModel(SomeToolingModel)

        then:
        modelB1.message == "It works from project :b"
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":")
            modelsCreated(":b")
        }

        when: "repeat query against 'a' — loads from :a's cache"
        withIsolatedProjects()
        executer.inDirectory(file("a"))
        def modelA2 = fetchModel(SomeToolingModel)

        then:
        modelA2.message == "It works from project :a"
        fixture.assertModelLoaded()

        when: "repeat query against 'b' — loads from :b's cache"
        withIsolatedProjects()
        executer.inDirectory(file("b"))
        def modelB2 = fetchModel(SomeToolingModel)

        then:
        modelB2.message == "It works from project :b"
        fixture.assertModelLoaded()
    }
}
