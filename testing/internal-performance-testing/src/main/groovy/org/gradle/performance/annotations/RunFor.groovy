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

package org.gradle.performance.annotations

import groovy.transform.CompileStatic
import org.gradle.performance.fixture.PerformanceTestScenarioDefinition
import org.gradle.performance.results.OperatingSystem
import org.junit.Assume
import org.spockframework.runtime.extension.ExtensionAnnotation
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Method

import static org.gradle.performance.fixture.PerformanceTestScenarioDefinition.PerformanceTestsBean.GroupsBean

/**
 * We scan all performance test classes annotated by @RunFor and generate the scenario definition JSON file (.teamcity/performance-tests-ci.json).
 * All performance test methods should be annotated by @RunFor, either at method or class level.
 *
 * The sanityCheck task checks (via `performance:verifyPerformanceScenarioDefinitions` task):
 * 1. All performance test methods are annotated by {@link @RunFor}. (except for {@link @NoRunFor} and {@link @Ignore} annotated methods).
 * 2. The performance scenario information provided by all {@link @RunFor}s exactly matches scenario definition JSON file, no more, no less.
 *
 * To update the scenario definition JSON file, run `./gradlew performance:writePerformanceScenarioDefinitions`.
 *
 * Also see {@link @NoRunFor}
 */
@Target([ElementType.TYPE, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@ExtensionAnnotation(RunForExtension.class)
@interface RunFor {
    Scenario[] value()
}

@Retention(RetentionPolicy.RUNTIME)
@interface Scenario {
    ScenarioType type()

    OperatingSystem[] operatingSystems()

    String[] testProjects() default []

    /**
     * Declare regular expressions matching the iteration name.
     * Defaults to an empty string, meaning this annotation applies to all iterations of the annotated feature.
     */
    String iterationMatcher() default "";

    String comment() default "";
}

enum ScenarioType {
    PER_COMMIT,
    PER_DAY,
    PER_WEEK
}

@CompileStatic
class RunForExtension implements IAnnotationDrivenExtension<RunFor> {
    private static final String SCENARIO_JSON_PROPERTY_NAME = "org.gradle.performance.scenario.json"
    private static final File SCENARIO_DEFINITION_FILE = System.getProperty(SCENARIO_JSON_PROPERTY_NAME) == null
        ? null : new File(System.getProperty(SCENARIO_JSON_PROPERTY_NAME))
    private static PerformanceTestScenarioDefinition scenarioDefinition = new PerformanceTestScenarioDefinition()

    static {
        Runtime.getRuntime().addShutdownHook(new Thread({
            if (SCENARIO_DEFINITION_FILE != null) {
                scenarioDefinition.writeTo(SCENARIO_DEFINITION_FILE)
            }
        } as Runnable))
    }

    @Override
    void visitSpecAnnotation(RunFor runFor, SpecInfo spec) {
        assert runFor.value().every { it.iterationMatcher().isEmpty() }: "No iterationMatchers allowed in class-level @Scenario!"
        for (FeatureInfo feature : spec.getFeatures()) {
            if (!feature.getFeatureMethod().getReflection().isAnnotationPresent(RunFor.class)) {
                visitFeatureAnnotation(runFor, feature)
            }
        }
    }

    @Override
    void visitFeatureAnnotation(RunFor runFor, FeatureInfo feature) {
        feature.getFeatureMethod().addInterceptor(new AddScenarioDefinitionInterceptor(feature.getFeatureMethod().getReflection(), runFor))
    }

    private static void verify(Scenario scenario) {
        assert scenario.testProjects().length != 0 && scenario.operatingSystems().length != 0: "Invalid scenario: $scenario"
    }

    private static GroupsBean createGroup(Scenario scenario, String testProject) {
        return new GroupsBean(
            testProject: testProject,
            coverage: [(scenario.type().toString().toLowerCase()): scenario.operatingSystems().collect { it.coverageName }] as TreeMap,
            comment: scenario.comment() ?: null
        )
    }

    static class AddScenarioDefinitionInterceptor implements IMethodInterceptor {
        private final Method method
        private final RunFor runFor

        AddScenarioDefinitionInterceptor(Method method, RunFor runFor) {
            this.method = method
            this.runFor = runFor
        }

        @Override
        void intercept(IMethodInvocation invocation) throws Throwable {
            if (SCENARIO_DEFINITION_FILE != null) {
                String testId = invocation.getIteration().getDisplayName()

                List<Scenario> scenarios = runFor.value().toList()
                List<GroupsBean> groups = []
                for (Scenario scenario : scenarios) {
                    verify(scenario)
                    String matcher = scenario.iterationMatcher()
                    if (matcher.isEmpty() || testId.matches(matcher)) {
                        scenario.testProjects().each {
                            groups.add(createGroup(scenario, it))
                        }
                    }
                }
                if (!groups.isEmpty()) {
                    scenarioDefinition.getPerformanceTests().add(new PerformanceTestScenarioDefinition.PerformanceTestsBean("${invocation.getSpec().getReflection().getName()}.$testId", groups))
                }
                Assume.assumeFalse(true)
            } else {
                invocation.proceed()
            }
        }
    }
}

