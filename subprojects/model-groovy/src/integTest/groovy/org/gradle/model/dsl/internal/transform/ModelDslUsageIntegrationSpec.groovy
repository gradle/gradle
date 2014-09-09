/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.dsl.internal.transform

import org.gradle.api.internal.project.AbstractProject
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

/**
 * Tests the valid and invalid usage of model {} WRT context.
 */
class ModelDslUsageIntegrationSpec extends AbstractIntegrationSpec {

    @Unroll
    def "model block must be top level statement in build script - #code"() {
        given:
        settingsFile << "include 'a', 'b'"

        when:
        buildScript """

        $code {
            model {}
        }

        """

        then:
        fails "tasks"
        failure.assertHasCause(AbstractProject.NON_TOP_LEVEL_MODEL_BLOCK_MESSAGE)

        where:
        code << [
            "subprojects",
            "project(':a')",
            "if (true)"
        ]
    }

    def "model block cannot be used from init script"() {
        when:
        file("init.gradle") << """
            allprojects {
                model {}
            }
        """

        then:
        args("-I", file("init.gradle").absolutePath)
        fails "tasks"
        failure.assertHasDescription(AbstractProject.NON_TOP_LEVEL_MODEL_BLOCK_MESSAGE)
    }
}
