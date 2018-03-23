/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.tooling.internal.provider

import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.BuildRequestContext
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ParallelismConfigurationManager
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.exec.BuildActionExecuter
import org.gradle.launcher.exec.BuildActionParameters
import spock.lang.Specification

class ParallelismConfigurationBuildActionExecuterTest extends Specification {
    def delegate = Mock(BuildActionExecuter)
    def action = Mock(BuildAction)
    def parallelExecutionManager = Mock(ParallelismConfigurationManager)
    def parallelismConfigurationBuildActionExecuter = new ParallelismConfigurationBuildActionExecuter(delegate, parallelExecutionManager)
    def buildRequestContext = Stub(BuildRequestContext)
    def buildActionParameters = Stub(BuildActionParameters)
    def contextServices = Stub(ServiceRegistry)

    def "sets parallelism configuration when executed and sets back to default when finished"() {
        when:
        parallelismConfigurationBuildActionExecuter.execute(action, buildRequestContext, buildActionParameters, contextServices)

        then:
        1 * action.startParameter >> Mock(StartParameterInternal) {
            1 * getMaxWorkerCount() >> 4
            1 * isParallelProjectExecutionEnabled() >> true
        }
        1 * parallelExecutionManager.setParallelismConfiguration(_) >> { args ->
            assert args[0].getMaxWorkerCount() == 4
            assert args[0].isParallelProjectExecutionEnabled()
        }

        then:
        1 * delegate.execute(action, buildRequestContext, buildActionParameters, contextServices)

        then:
        1 * parallelExecutionManager.setParallelismConfiguration(_) >> { args ->
            assert args[0].getMaxWorkerCount() == DefaultParallelismConfiguration.DEFAULT.maxWorkerCount
            assert args[0].isParallelProjectExecutionEnabled() == DefaultParallelismConfiguration.DEFAULT.parallelProjectExecutionEnabled
        }
    }
}
