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

import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.plugins.jvm.testing.toolchains.JVMTestToolchainParameters;
import org.gradle.api.tasks.testing.Test;

abstract public class LegacyJUnit4TestToolchain implements JVMTestToolchain<JVMTestToolchainParameters.None> {
    @Override
    public TestFramework createTestFramework(Test task) {
        return new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter(), true);
    }
}
