/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.test.preconditions

import groovy.transform.CompileStatic
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.test.precondition.TestPrecondition

@CompileStatic
class NativeTestPreconditions {

    /**
     * Satisfied on non-Windows platforms, or on Windows when Visual C++ 2015 is available.
     * Used for the Google Test sample which only works on Windows with VS2015.
     */
    static final class NotWindowsOrVisualCpp2015 implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return TestPrecondition.satisfied(UnitTestPreconditions.NotWindows)
                || AvailableToolChains.getToolChain(ToolChainRequirement.VISUALCPP_2015) != null
        }
    }
}
