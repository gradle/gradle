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

package org.gradle.integtests.resolve.fixture;

import org.gradle.integtests.fixtures.AbstractMultiTestRunner;

/**
 * This runner runs tests with early dependency graph resolution run both
 * forced and not.  Any test classes that use this runner will have tests
 * run twice.
 */
public class EarlyDependencyGraphResolveRunner extends AbstractMultiTestRunner {
    public EarlyDependencyGraphResolveRunner(Class<?> target) {
        super(target)
    }

    @Override
    protected void createExecutions() {
        // Run the test once with early dependency forced and once without
        add(new ResolveDependencyGraphExecution(true))
        add(new ResolveDependencyGraphExecution(false))
    }

    private class ResolveDependencyGraphExecution extends AbstractMultiTestRunner.Execution {
        final static String FORCE_DEPENDENCY_RESOLVE_PROP = "org.gradle.forceEarlyDependencyResolve"
        final boolean forceEarlyDependencyResolve

        public ResolveDependencyGraphExecution(boolean forceEarlyDependencyResolve) {
            this.forceEarlyDependencyResolve = forceEarlyDependencyResolve
        }

        @Override
        protected String getDisplayName() {
            return forceEarlyDependencyResolve ? "force early dependency graph resolve" : "normal dependency graph resolve"
        }

        @Override
        protected void before() {
            System.setProperty(FORCE_DEPENDENCY_RESOLVE_PROP, forceEarlyDependencyResolve.toString())
        }

        @Override
        protected void after() {
            System.properties.remove(FORCE_DEPENDENCY_RESOLVE_PROP)
        }
    }
}
