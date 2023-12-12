/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.isolated

import org.gradle.integtests.fixtures.build.KotlinDslTestProjectInitiation
import org.gradle.tooling.model.kotlin.dsl.EditorReport
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.integtests.tooling.fixture.ToolingApiModelChecker.checkModel

class IsolatedProjectsToolingApiKotlinDslIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest implements KotlinDslTestProjectInitiation {

    def "can fetch KotlinDslScripts model for single subproject build"() {
        withSettings("""
            include("a")
        """)
        withBuildScript()
        withBuildScriptIn("a")

        when: "fetching without Isolated Projects"
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def model = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertStateStored {
            modelsCreated(":", 5)
            modelsCreated(":a", 2)
        }

        checkKotlinDslScriptsModel(model, originalModel)

        when: "fetching again with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch KotlinDslScripts model for multi-project build"() {
        withMultiProjectBuildWithBuildSrc()

        when: "fetching without Isolated Projects"
        def originalModel = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertNoConfigurationCache()

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def model = fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            modelsCreated(":", 5)
            modelsCreated(":a", 2)
            modelsCreated(":b", 3)
        }

        checkKotlinDslScriptsModel(model, originalModel)

        when: "fetching again with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        fetchModel(KotlinDslScriptsModel)

        then:
        fixture.assertStateLoaded()
    }

    static void checkKotlinDslScriptsModel(actual, expected) {
        assert expected instanceof KotlinDslScriptsModel
        assert actual instanceof KotlinDslScriptsModel

        checkModel(actual, expected, [
            [{ it.scriptModels }, { a, e -> checkKotlinDslScriptModel(a, e) }]
        ])
    }

    static void checkKotlinDslScriptModel(actual, expected) {
        assert expected instanceof KotlinDslScriptModel
        assert actual instanceof KotlinDslScriptModel

        checkModel(actual, expected, [
            { it.classPath },
            { it.sourcePath },
            { it.implicitImports },
            [{ it.editorReports }, { a, e -> checkEditorReport(a, e) }],
            { it.exceptions },
        ])
    }

    static void checkEditorReport(actual, expected) {
        assert expected instanceof EditorReport
        assert actual instanceof EditorReport

        checkModel(actual, expected, [
            { it.severity },
            { it.message },
            [{ it.position }, [
                { it.line },
                { it.column },
            ]]
        ])
    }
}
