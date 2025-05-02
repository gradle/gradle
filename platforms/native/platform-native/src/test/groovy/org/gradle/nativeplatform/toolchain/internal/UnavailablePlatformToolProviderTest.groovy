/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal

import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import spock.lang.Specification


class UnavailablePlatformToolProviderTest extends Specification {
    def "can construct with failure message"() {
        def provider = new UnavailablePlatformToolProvider(Stub(OperatingSystemInternal), new ToolChainAvailability().unavailable("don't know how to build this thing"))

        expect:
        def formatter = new TreeFormatter()
        provider.explain(formatter)
        formatter.toString() == "don't know how to build this thing"
    }

    def "tool lookup returns same failure"() {
        def provider = new UnavailablePlatformToolProvider(Stub(OperatingSystemInternal), new ToolChainAvailability().unavailable("don't know how to build this thing"))

        expect:
        def toolResult = provider.locateTool(ToolType.C_COMPILER)
        !toolResult.available
        def formatter = new TreeFormatter()
        toolResult.explain(formatter)
        formatter.toString() == "don't know how to build this thing"
    }
}
