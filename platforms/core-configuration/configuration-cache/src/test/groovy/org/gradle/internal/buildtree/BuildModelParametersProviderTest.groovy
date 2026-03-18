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
import org.gradle.api.GradleException
import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.buildoption.Option
import org.gradle.internal.buildtree.control.BuildModelParametersProvider
import spock.lang.Specification

class BuildModelParametersProviderTest extends Specification {

    def defaults() {
        [
            parallelProjectExecution: false,
            configureOnDemand: false,

            configurationCache: false,
            configurationCacheDisabledReason: null,
            configurationCacheParallelStore: false,
            configurationCacheParallelLoad: false,

            isolatedProjects: false,
            parallelProjectConfiguration: false,
            invalidateCoupledProjects: false,
            modelAsProjectDependency: false,

            modelBuilding: false,
            parallelModelBuilding: false,
            cachingModelBuilding: false,
            resilientModelBuilding: false,
        ]
    }

    def "default parameters for #description"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models)

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: models
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true

        description = tasks && models ? "running tasks and building models" : (tasks ? 'running tasks' : 'building models')
    }

    def "configure on demand is disabled when building models"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            configureOnDemand = true
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            configureOnDemand: !models,
            modelBuilding: models,
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true
    }

    def "parallel execution flag enables parallel model building when building models"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            parallelProjectExecutionEnabled = true
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            parallelProjectExecution: true,
            modelBuilding: models,
            parallelModelBuilding: models,
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true
    }

    def "with parallel execution flag, can disable parallel model building with ignore-legacy-default flag"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            parallelProjectExecutionEnabled = true
            systemPropertiesArgs[BuildModelParametersProvider.parallelModelBuildingIgnoreLegacyDefault] = "true"
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            parallelProjectExecution: true,
            modelBuilding: models,
            parallelModelBuilding: false,
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true
    }

    def "with parallel execution flag, can disable parallel model building with a dedicated property"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            parallelProjectExecutionEnabled = true
            parallelToolingModelBuilding = Option.Value.value(false)
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            parallelProjectExecution: true,
            modelBuilding: models,
            parallelModelBuilding: false,
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true
    }

    def "can enable parallel model building with a dedicated property"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            parallelToolingModelBuilding = Option.Value.value(true)
            parallelProjectExecutionEnabled = false // since there is no explicit value tracking for this, the value is ignored even if explicit
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: models,
            parallelModelBuilding: models,
            parallelProjectExecution: models, // enabled automatically, because it's required for nested tooling actions parallelism
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true
    }

    def "parameters when configuration cache is enabled for running tasks"() {
        given:
        def params = parameters(runsTasks: true, createsModel: false) {
            setConfigurationCache(Option.Value.value(true))
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            configurationCache: true,
            configurationCacheParallelLoad: true,
            configurationCacheParallelStore: false,
            parallelProjectExecution: false, // With CC, tasks are known to be isolated, so they run in parallel even without "parallel execution"
        ])
    }

    def "configuration cache is automatically disabled when building models"() {
        given:
        def params = parameters(runsTasks: true, createsModel: true) {
            setConfigurationCache(Option.Value.value(true))
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: true,
            configurationCache: false,
        ])

        where:
        tasks << [true, false]

        description = tasks ? "running tasks and building models" : 'building models'
    }

    def "configuration cache is automatically disabled when combined with --#option"() {
        given:
        def params = parameters(runsTasks: true, createsModel: false) {
            setConfigurationCache(Option.Value.value(true))
            configureStartParameter(it)
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            configurationCache: false,
            configurationCacheDisabledReason: "due to --$option"
        ])

        where:
        option                        | configureStartParameter
        "export-keys"                 | { it.setExportKeys(true) }
        "property-upgrade-report"     | { it.setPropertyUpgradeReportEnabled(true) }
        "write-verification-metadata" | { it.setWriteDependencyVerifications(["checksum"]) }
    }

    def "parameters when isolated projects are enabled for #description"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            isolatedProjects = Option.Value.value(true)
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: models,
            parallelProjectExecution: true,
            configurationCache: true,
            configurationCacheParallelStore: true,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: true,
            parallelModelBuilding: models,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: models
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true

        description = tasks && models ? "running tasks and building models" : (tasks ? 'running tasks' : 'building models')
    }

    def "with isolated projects, disabling parallel execution is ignored"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            isolatedProjects = Option.Value.value(true)
            parallelProjectExecutionEnabled = false // since there is no explicit value tracking for this, the value is ignored even if explicitly set
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: models,
            parallelProjectExecution: true,
            configurationCache: true,
            configurationCacheParallelStore: true,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: true,
            parallelModelBuilding: models,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: models
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true
    }

    def "with isolated projects, disabling parallel model building is ignored"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            isolatedProjects = Option.Value.value(true)
            parallelToolingModelBuilding = Option.Value.value(false)
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: models,
            parallelProjectExecution: true,
            configurationCache: true,
            configurationCacheParallelStore: true,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: true,
            parallelModelBuilding: models,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: models
        ])

        where:
        tasks | models
        true  | false
        false | true
        true  | true
    }

    def "parameters when isolated projects are enabled for #description with configure-on-demand-ip=#ipConfigureOnDemand"() {
        given:
        def params = parameters(runsTasks: tasks, createsModel: models) {
            isolatedProjects = Option.Value.value(true)
            systemPropertiesArgs[BuildModelParametersProvider.isolatedProjectsConfigureOnDemand.propertyName] = ipConfigureOnDemand
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: models,
            configureOnDemand: configureOnDemandExpected,
            parallelProjectExecution: true,
            configurationCache: true,
            configurationCacheParallelStore: true,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: true,
            parallelModelBuilding: models,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: models
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
            systemPropertiesArgs[BuildModelParametersProvider.isolatedProjectsParallel.propertyName] = ipParallel
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: models,
            parallelProjectExecution: ipParallelExpected,
            configurationCache: true,
            configurationCacheParallelStore: ipParallelExpected,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: ipParallelExpected,
            parallelModelBuilding: ipParallelExpected && models,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: models
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
            systemPropertiesArgs[BuildModelParametersProvider.isolatedProjectsCaching.propertyName] = ipCaching
        }

        expect:
        checkParameters(params.toDisplayMap(), defaults() + [
            modelBuilding: models,
            parallelProjectExecution: true,
            configurationCache: true,
            configurationCacheParallelStore: true,
            configurationCacheParallelLoad: true,
            isolatedProjects: true,
            parallelProjectConfiguration: true,
            parallelModelBuilding: models,
            cachingModelBuilding: ipCachingExpected,
            invalidateCoupledProjects: true,
            modelAsProjectDependency: models
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
            isolatedProjects = Option.Value.value(true)
            systemPropertiesArgs[BuildModelParametersProvider.isolatedProjectsCaching.propertyName] = value
        }

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Unsupported value for 'org.gradle.internal.isolated-projects.caching' option")

        where:
        value << ['true', 'tasks']
    }

    def "configuration cache cannot be disabled when isolated projects enabled"() {
        when:
        parameters(runsTasks: true, createsModel: false) {
            isolatedProjects = Option.Value.value(true)
            configurationCache = Option.Value.value(false)
        }

        then:
        def e = thrown(GradleException)
        e.message == "Configuration Cache cannot be disabled when Isolated Projects is enabled."
    }

    def "display map contains all parameter getters"() {
        def expectedGetterCount =
            BuildModelParameters.methods.count { it.name.matches(/^(is|get)[A-Z].*/) && it.name != 'getClass' }

        expect:
        def params = parameters(runsTasks: true, createsModel: false)
        expectedGetterCount == params.toDisplayMap().size()
    }

    private BuildModelParameters parameters(
        Map args,
        @DelegatesTo(StartParameterInternal)
        @ClosureParams(value = SimpleType, options = "org.gradle.api.internal.StartParameterInternal")
            Closure startParameterConfig = {}
    ) {
        boolean runsTasks = args.runsTasks
        boolean createsModel = args.createsModel
        def startParameter = new StartParameterInternal()
        startParameter.with(startParameterConfig)
        def options = new DefaultInternalOptions(startParameter.systemPropertiesArgs)
        return BuildModelParametersProvider.parameters(requirements(runsTasks, createsModel, startParameter), options)
    }

    private BuildActionModelRequirements requirements(boolean runsTasks, boolean createsModel, StartParameterInternal startParameter) {
        def requirements = Mock(BuildActionModelRequirements)
        requirements.isRunsTasks() >> runsTasks
        requirements.isCreatesModel() >> createsModel
        requirements.getStartParameter() >> startParameter
        return requirements
    }

    private static void checkParameters(Map<String, Object> actual, Map<String, Object> expected) {
        // sorting is not required, but useful for better diff in case of failures
        assert actual.sort() == expected.sort()
    }
}
