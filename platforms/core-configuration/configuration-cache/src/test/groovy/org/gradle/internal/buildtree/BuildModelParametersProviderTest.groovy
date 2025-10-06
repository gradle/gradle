/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.buildtree


import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.LogLevel
import org.gradle.internal.buildoption.Option
import org.gradle.internal.cc.buildtree.BuildModelParametersProvider
import spock.lang.Specification

class BuildModelParametersProviderTest extends Specification {

    def "default parameters for #description"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models)

        expect:
        checkParameters(params.toDisplayMap(), [
            requiresToolingModels: models,
            configureOnDemand: false,
            parallelProjectExecution: false,
            configurationCache: false,
            configurationCacheParallelStore: false,
            configurationCacheParallelLoad: true,
            isolatedProjects: false,
            parallelProjectConfiguration: false,
            parallelToolingApiActions: false,
            intermediateModelCache: false,
            invalidateCoupledProjects: false,
            modelAsProjectDependency: false,
            resilientModelBuilding: false
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true

        description = tasks && models ? "running tasks and building models" : (tasks ? 'running tasks' : 'building models')
    }


    def "parameters when isolated projects are enabled for #description"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            isolatedProjects = Option.Value.value(true)
        }

        expect:
        checkParameters(params.toDisplayMap(), [
            requiresToolingModels: models,
            configureOnDemand: false,
            parallelProjectExecution: true,
            configurationCache: true,
            configurationCacheParallelStore: true,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: true,
            parallelToolingApiActions: true,
            intermediateModelCache: models,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: true,
            resilientModelBuilding: false
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true

        description = tasks && models ? "running tasks and building models" : (tasks ? 'running tasks' : 'building models')
    }

    def "parameters when isolated projects are enabled for #description with configure-on-demand-ip=#ipConfigureOnDemand"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            isolatedProjects = Option.Value.value(true)
            systemPropertiesArgs[BuildModelParametersProvider.isolatedProjectsConfigureOnDemand.systemPropertyName] = ipConfigureOnDemand
        }

        expect:
        checkParameters(params.toDisplayMap(), [
            requiresToolingModels: models,
            configureOnDemand: configureOnDemandExpected,
            parallelProjectExecution: true,
            configurationCache: true,
            configurationCacheParallelStore: true,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: true,
            parallelToolingApiActions: true,
            intermediateModelCache: models,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: true,
            resilientModelBuilding: false
        ])

        where:
        tasks | models | ipConfigureOnDemand | configureOnDemandExpected
        true  | false  | "true"              | true
        true  | false  | "tasks"             | true
        true  | false  | "tooling"           | false
        true  | false  | "false"             | false
        false | true   | "true"              | true
        false | true   | "tasks"             | false
        false | true   | "tooling"           | true
        false | true   | "false"             | false
        true  | true   | "true"              | true
        true  | true   | "tasks"             | false
        true  | true   | "tooling"           | false
        true  | true   | "false"             | false

        description = tasks && models ? "running tasks and building models" : (tasks ? 'running tasks' : 'building models')
    }

    def "parameters when isolated projects are enabled for #description with parallel-ip=#ipParallel"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            isolatedProjects = Option.Value.value(true)
            systemPropertiesArgs[BuildModelParametersProvider.isolatedProjectsParallel.systemPropertyName] = ipParallel
        }

        expect:
        checkParameters(params.toDisplayMap(), [
            requiresToolingModels: models,
            configureOnDemand: false,
            parallelProjectExecution: ipParallelExpected,
            configurationCache: true,
            configurationCacheParallelStore: ipParallelExpected,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: ipParallelExpected,
            parallelToolingApiActions: ipParallelExpected,
            intermediateModelCache: models,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: true,
            resilientModelBuilding: false
        ])

        where:
        tasks | models | ipParallel | ipParallelExpected
        true  | false  | "true"     | true
        true  | false  | "tasks"    | true
        true  | false  | "tooling"  | false
        true  | false  | "false"    | false
        false | true   | "true"     | true
        false | true   | "tasks"    | false
        false | true   | "tooling"  | true
        false | true   | "false"    | false
        true  | true   | "true"     | true
        true  | true   | "tasks"    | false
        true  | true   | "tooling"  | false
        true  | true   | "false"    | false

        description = tasks && models ? "running tasks and building models" : (tasks ? 'running tasks' : 'building models')
    }

    def "parameters when isolated projects are enabled for #description with caching-ip=#ipCaching"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            isolatedProjects = Option.Value.value(true)
            systemPropertiesArgs[BuildModelParametersProvider.isolatedProjectsCaching.systemPropertyName] = ipCaching
        }

        expect:
        checkParameters(params.toDisplayMap(), [
            requiresToolingModels: models,
            configureOnDemand: false,
            parallelProjectExecution: true,
            configurationCache: true,
            configurationCacheParallelStore: true,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: true,
            parallelToolingApiActions: true,
            intermediateModelCache: ipCachingExpected,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: true,
            resilientModelBuilding: false
        ])

        where:
        tasks | models | ipCaching | ipCachingExpected
        true  | false  | "tooling" | false
        true  | false  | "false"   | false
        false | true   | "tooling" | true
        false | true   | "false"   | false
        true  | true   | "tooling" | true
        true  | true   | "false"   | false

        description = tasks && models ? "running tasks and building models" : (tasks ? 'running tasks' : 'building models')
    }

    def "caching-ip parameter is unsupported for #value"() {
        when:
        parameters(runsTasks: true, createsModel: false) {
            systemPropertiesArgs[BuildModelParametersProvider.isolatedProjectsCaching.systemPropertyName] = value
        }

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Unsupported value for 'org.gradle.internal.isolated-projects.caching' option")

        where:
        value << ['true', 'tasks']
    }

    private BuildModelParameters parameters(
        Map args,
        @DelegatesTo(StartParameterInternal)
        @ClosureParams(value = SimpleType, options = "org.gradle.api.internal.StartParameterInternal")
            Closure startParameterConfig = {}
    ) {
        boolean runsTasks = args.runsTasks
        boolean createsModel = args.createsModel
        LogLevel logLevel = (args.logLevel as LogLevel) ?: LogLevel.QUIET

        def requirements = requirements(runsTasks, createsModel, startParameterConfig)
        return BuildModelParametersProvider.parameters(requirements, requirements.startParameter, logLevel)
    }

    private BuildActionModelRequirements requirements(boolean runsTasks, boolean createsModel, Closure startParameterConfig) {
        def startParameter = new StartParameterInternal()
        startParameter.with(startParameterConfig)
        return requirements(runsTasks, createsModel, startParameter)
    }

    private BuildActionModelRequirements requirements(boolean runsTasks, boolean createsModel, StartParameterInternal startParameter) {
        def requirements = Mock(BuildActionModelRequirements)
        requirements.isRunsTasks() >> runsTasks
        requirements.isCreatesModel() >> createsModel
        requirements.getStartParameter() >> startParameter
        return requirements
    }

    private static void checkParameters(Map<String, Boolean> actual, Map<String, Boolean> expected) {
        // sorting is not required, but useful for better diff in case of failures
        assert actual.sort() == expected.sort()
    }
}
