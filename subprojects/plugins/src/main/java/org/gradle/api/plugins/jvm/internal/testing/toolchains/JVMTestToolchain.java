/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal.testing.toolchains;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.plugins.jvm.testing.toolchains.JVMTestToolchainParameters;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.Actions;

import javax.inject.Inject;
import java.util.Collections;

public interface JVMTestToolchain<T extends JVMTestToolchainParameters> {
    TestFramework createTestFramework(Test task);

    default Iterable<Dependency> getCompileOnlyDependencies() {
        return Collections.emptyList();
    };

    default Iterable<Dependency> getRuntimeOnlyDependencies() {
        return Collections.emptyList();
    };

    default Iterable<Dependency> getImplementationDependencies() {
        return Collections.emptyList();
    }

    @Inject
    T getParameters();

    default Action<Test> getTestTaskConfiguration() {
        return Actions.doNothing();
    };
}
