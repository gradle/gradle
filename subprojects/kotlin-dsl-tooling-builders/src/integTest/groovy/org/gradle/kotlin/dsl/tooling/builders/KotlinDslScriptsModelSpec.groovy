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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.build.TestProjectInitiation
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.junit.Rule
import org.junit.rules.RuleChain

class KotlinDslScriptsModelSpec extends AbstractIntegrationSpec implements KotlinScriptsModelFetcher, TestProjectInitiation {
    def toolingApi = new ToolingApi(distribution, temporaryFolder)

    @Rule
    public RuleChain cleanupRule = RuleChain.outerRule(temporaryFolder).around(toolingApi)

    def <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        try {
            toolingApi.withConnection(cl)
        } catch (GradleConnectionException e) {
            throw e
        }
    }

    def 'exceptions in different scripts are reported on the corresponding scripts'() {

        given:
        toolingApi.requireIsolatedUserHome()

        when:
        def spec = withMultiProject()
        spec.scripts["a"] << "throw RuntimeException(\"ex1\")"
        spec.scripts["b"] << "throw RuntimeException(\"ex2\")"

        Map<File, KotlinDslScriptModel> singleRequestModels = kotlinDslScriptsModelFor(true).scriptModels

        then:

        singleRequestModels[spec.scripts["a"]].exceptions.size() == 1
        singleRequestModels[spec.scripts["b"]].exceptions.size() == 1
        singleRequestModels[spec.scripts["settings"]].exceptions.isEmpty()
    }

}
