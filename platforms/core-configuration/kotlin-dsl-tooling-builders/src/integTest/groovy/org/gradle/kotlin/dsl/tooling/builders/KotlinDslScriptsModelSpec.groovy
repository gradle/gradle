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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.build.KotlinDslTestProjectInitiation
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.junit.Rule
import org.junit.rules.RuleChain

class KotlinDslScriptsModelSpec extends AbstractIntegrationSpec implements KotlinDslTestProjectInitiation {
    @Delegate
    ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)
    TestOutputStream stderr = new TestOutputStream()
    TestOutputStream stdout = new TestOutputStream()

    @Rule
    public RuleChain cleanupRule = RuleChain.outerRule(temporaryFolder).around(toolingApi)

    def 'exceptions in different scripts are reported on the corresponding scripts'() {

        given:
        requireIsolatedUserHome()

        when:
        def spec = withMultipleSubprojects()
        spec.scripts["a"] << "throw RuntimeException(\"ex1\")"
        spec.scripts["b"] << "throw RuntimeException(\"ex2\")"


        def model = loadValidatedToolingModel(KotlinDslScriptsModel) {
            KotlinScriptModelParameters.setModelParameters(it, true, true, [])
        }

        Map<File, KotlinDslScriptsModel> singleRequestModels = model.scriptModels

        then:

        singleRequestModels[spec.scripts["a"]].exceptions.size() == 1
        singleRequestModels[spec.scripts["b"]].exceptions.size() == 1
        singleRequestModels[spec.scripts["settings"]].exceptions.isEmpty()
    }
}
