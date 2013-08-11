/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.toolchain.plugins

import org.gradle.nativecode.toolchain.internal.msvcpp.VisualCppToolChain
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TestUtil
import spock.lang.Specification

class MicrosoftVisualCppPluginTest extends Specification {
    def project = TestUtil.createRootProject()

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "installs a no-op tool chain when not windows"() {
        when:
        project.plugins.apply(MicrosoftVisualCppPlugin)

        then:
        def visualCpp = project.toolChains.visualCpp
        !visualCpp.availability.available
        visualCpp.availability.unavailableMessage == 'Not available on this operating system.'
        visualCpp.toString() == "ToolChain 'visualCpp' (Visual C++)"
    }

    @Requires(TestPrecondition.WINDOWS)
    def "installs a real tool chain when windows"() {
        when:
        project.plugins.apply(MicrosoftVisualCppPlugin)

        then:
        def visualCpp = project.toolChains.visualCpp
        visualCpp instanceof VisualCppToolChain
        visualCpp.toString() == "ToolChain 'visualCpp' (Visual C++)"
    }
}
