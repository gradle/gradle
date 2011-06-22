/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures.internal

import org.gradle.CacheUsage
import org.gradle.StartParameter
import org.gradle.integtests.fixtures.*
import org.gradle.util.TestFile
import org.junit.Rule

import spock.lang.*

/**
 * Spockified version of AbstractIntegrationTest.
 * 
 * Plan is to bring features over as needed.
 */
class AbstractIntegrationSpec extends Specification {
    
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    ExecutionResult result
    ExecutionFailure failure

    protected GradleExecuter buildScript(String script) {
        executer.usingBuildScript(script)
    }

    protected ExecutionResult succeeds(String... tasks) {
        result = executer.withTasks(*tasks).run()
    }
    
    protected List<String> getExecutedTasks() {
        assertHasResult()
        result.executedTasks
    }
    
    protected Set<String> getSkippedTasks() {
        assertHasResult()
        result.skippedTasks
    }
    
    private assertHasResult() {
        assert result != null : "result is null, you haven't run succeeds()"
    }

}