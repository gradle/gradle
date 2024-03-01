/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.legacy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ScopeIdsFixture
import org.gradle.internal.scan.scopeids.BuildScanScopeIds
import org.junit.Rule

class BuildScanScopeIdsIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final ScopeIdsFixture scopeIds = new ScopeIdsFixture(executer, temporaryFolder)

    def "exposes scans view of scope IDs"() {
        when:
        buildScript """
            def ids = project.gradle.services.get($BuildScanScopeIds.name)
            println "ids: [buildInvocation: \$ids.buildInvocationId, workspace: \$ids.workspaceId, user: \$ids.userId]"
        """
        succeeds("help")

        then:
        def buildInvocationId = scopeIds.buildInvocationId.asString()
        def workspaceId = scopeIds.workspaceId.asString()
        def userId = scopeIds.userId.asString()

        output.contains "ids: [buildInvocation: $buildInvocationId, workspace: $workspaceId, user: $userId]"
    }


}
