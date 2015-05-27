/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.fixtures
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
/**
 * Runs tests with fluid dependencies enabled and disabled: when fluid dependencies are enabled, any configuration that
 * is a task input is resolved when constructing the task graph.
 * Test classes that use this runner will have tests run twice, with and without fluid dependencies enabled.
 */
public class FluidDependenciesResolveRunner extends AbstractMultiTestRunner {
    public final static String ASSUME_FLUID_DEPENDENCIES = "org.gradle.resolution.assumeFluidDependencies"

    public FluidDependenciesResolveRunner(Class<?> target) {
        super(target)
        // Ensure that the system property is propagated to forked Gradle executions
        AbstractGradleExecuter.propagateSystemProperty(ASSUME_FLUID_DEPENDENCIES);
    }

    @Override
    protected void createExecutions() {
        // Run the test once with early dependency forced and once without
        add(new ResolveDependencyGraphExecution(true))
        add(new ResolveDependencyGraphExecution(false))
    }

    private class ResolveDependencyGraphExecution extends AbstractMultiTestRunner.Execution {
        final boolean assumeFluidDependencies

        public ResolveDependencyGraphExecution(boolean assumeFluidDependencies) {
            this.assumeFluidDependencies = assumeFluidDependencies
        }

        @Override
        protected String getDisplayName() {
            return assumeFluidDependencies ? "with fluid dependencies" : "without fluid dependencies"
        }

        @Override
        protected void before() {
            System.setProperty(ASSUME_FLUID_DEPENDENCIES, String.valueOf(assumeFluidDependencies))
        }

        @Override
        protected void after() {
            System.properties.remove(ASSUME_FLUID_DEPENDENCIES)
        }
    }
}
