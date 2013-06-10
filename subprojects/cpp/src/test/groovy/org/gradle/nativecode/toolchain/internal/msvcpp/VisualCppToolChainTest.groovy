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

package org.gradle.nativecode.toolchain.internal.msvcpp

import org.gradle.internal.os.OperatingSystem
import spock.lang.Specification
import org.gradle.internal.Factory

class VisualCppToolChainTest extends Specification {
    final toolChain = new VisualCppToolChain(new OperatingSystem.Windows(), Stub(Factory))

    def "uses .lib file for shared library at link time"() {
        expect:
        toolChain.getSharedLibraryLinkFileName("test") == "test.lib"
        toolChain.getSharedLibraryLinkFileName("test.dll") == "test.lib"
    }

    def "uses .dll file for shared library at runtime time"() {
        expect:
        toolChain.getSharedLibraryName("test") == "test.dll"
        toolChain.getSharedLibraryName("test.dll") == "test.dll"
    }
}
