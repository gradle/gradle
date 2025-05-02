/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.cc.impl.fixtures.ToolingApiBackedGradleExecuter
import org.gradle.internal.cc.impl.fixtures.ToolingApiSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter

class AbstractIsolatedProjectsToolingApiIntegrationTest extends AbstractIsolatedProjectsIntegrationTest implements ToolingApiSpec {

    static final String CONFIGURE_ON_DEMAND_FOR_TOOLING = "-Dorg.gradle.internal.isolated-projects.configure-on-demand.tooling=true"

    @Override
    void withIsolatedProjects(String... moreExecuterArgs) {
        executer.withArguments(ENABLE_CLI, CONFIGURE_ON_DEMAND_FOR_TOOLING, *moreExecuterArgs)
    }

    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }
}
