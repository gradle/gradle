/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.swift.metadata

import org.gradle.api.provider.Property
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import spock.lang.Specification

class SwiftcMetadataProviderTest extends Specification {

    def execActionFactory = Mock(ExecActionFactory)

    private static final String SWIFTC_OUTPUT_MAC_OS = """Apple Swift version 4.0.2 (swiftlang-900.0.69.2 clang-900.0.38)
Target: x86_64-apple-macosx10.9
    """

    private static final String SWIFTC_OUTPUT_LINUX = """Swift version 3.1.1 (swift-3.1.1-RELEASE)
Target: x86_64-unknown-linux-gnu
    """

    def "can scrape version from output of swiftc"() {
        expect:
        output(SWIFTC_OUTPUT_MAC_OS).component.vendor == 'Apple Swift version 4.0.2 (swiftlang-900.0.69.2 clang-900.0.38)'
        output(SWIFTC_OUTPUT_LINUX).component.vendor == 'Swift version 3.1.1 (swift-3.1.1-RELEASE)'
    }

    def "can parse version from output of swiftc"() {
        expect:
        output(SWIFTC_OUTPUT_MAC_OS).component.version.with { [major, minor, micro] } == [4, 0, 2]
        output(SWIFTC_OUTPUT_LINUX).component.version.with { [major, minor, micro] } == [3, 1, 1]
    }

    def "handles output that cannot be parsed"() {
        def visitor = new TreeFormatter()

        expect:
        def result = output(out)
        !result.available

        when:
        result.explain(visitor)

        then:
        visitor.toString() == "Could not determine SwiftC metadata: swiftc produced unexpected output."

        where:
        out << ["not sure about this", ""]
    }

    def "handles failure to execute swiftc"() {
        given:
        def visitor = new TreeFormatter()
        def action = Mock(ExecAction)
        def execResult = Mock(ExecResult)

        and:
        def metadataProvider = new SwiftcMetadataProvider(execActionFactory)
        def binary = new File("swiftc")

        when:
        def result = metadataProvider.getCompilerMetaData([]) { it.executable(binary) }

        then:
        1 * execActionFactory.newExecAction() >> action
        1 * action.execute() >> execResult
        1 * action.getStandardOutput() >> _
        1 * action.getErrorOutput() >> _
        1 * action.getIgnoreExitValue() >> _
        1 * execResult.getExitValue() >> 1

        and:
        !result.available

        when:
        result.explain(visitor)

        then:
        visitor.toString() == "Could not determine SwiftC metadata: failed to execute swiftc --version."
    }

    SearchResult<SwiftcMetadata> output(String output) {
        def action = Mock(ExecAction)
        def standardOutput = Mock(Property)
        def result = Mock(ExecResult)
        1 * execActionFactory.newExecAction() >> action
        1 * action.getStandardOutput() >> standardOutput
        1 * action.getErrorOutput() >> _
        1 * action.getIgnoreExitValue() >> _
        1 * standardOutput.set(_) >> { OutputStream outstr -> outstr << output }
        1 * action.execute() >> result
        def provider = new SwiftcMetadataProvider(execActionFactory)
        provider.getCompilerMetaData([]) { it.executable(new File("swiftc")) }
    }

}
