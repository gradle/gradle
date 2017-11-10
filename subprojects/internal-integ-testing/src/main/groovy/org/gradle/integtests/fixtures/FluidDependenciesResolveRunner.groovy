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

import groovy.transform.CompileStatic

/**
 * Runs tests with fluid dependencies enabled and disabled: when fluid dependencies are enabled, any configuration that
 * is a task input is resolved when constructing the task graph.
 * Test classes that use this runner will have tests run twice, with and without fluid dependencies enabled.
 */
@CompileStatic
class FluidDependenciesResolveRunner extends BehindFlagFeatureRunner {
    public final static String ASSUME_FLUID_DEPENDENCIES = "org.gradle.resolution.assumeFluidDependencies"

    FluidDependenciesResolveRunner(Class<?> target) {
        super(target, ASSUME_FLUID_DEPENDENCIES, "fluid dependencies")
    }

    static isFluid() {
        return System.getProperty(ASSUME_FLUID_DEPENDENCIES) == "true"
    }
}
