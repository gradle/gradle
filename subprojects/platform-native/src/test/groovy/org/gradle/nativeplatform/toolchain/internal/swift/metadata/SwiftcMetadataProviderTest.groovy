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

import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.TreeVisitor
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
        output(SWIFTC_OUTPUT_MAC_OS).version == 'Apple Swift version 4.0.2 (swiftlang-900.0.69.2 clang-900.0.38)'
        output(SWIFTC_OUTPUT_LINUX).version == 'Swift version 3.1.1 (swift-3.1.1-RELEASE)'
    }

    def "handles output that cannot be parsed"() {
        def visitor = Mock(TreeVisitor)

        expect:
        def result = output(out)
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine SwiftC metadata: swiftc produced unexpected output.")

        where:
        out << ["not sure about this", ""]
    }

    def "handles failure to execute swiftc"() {
        given:
        def visitor = Mock(TreeVisitor)
        def action = Mock(ExecAction)
        def execResult = Mock(ExecResult)

        and:
        def metadataProvider = new SwiftcMetadataProvider(execActionFactory)
        def binary = new File("swiftc")

        when:
        def result = metadataProvider.getCompilerMetaData(binary, [])

        then:
        1 * execActionFactory.newExecAction() >> action
        1 * action.execute() >> execResult
        1 * execResult.getExitValue() >> 1

        and:
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not determine SwiftC metadata: failed to execute swiftc --version.")
    }

    SwiftcMetadata output(String output) {
        def action = Mock(ExecAction)
        def result = Mock(ExecResult)
        1 * execActionFactory.newExecAction() >> action
        1 * action.setStandardOutput(_) >> { OutputStream outstr -> outstr << output; action }
        1 * action.execute() >> result
        new SwiftcMetadataProvider(execActionFactory).getCompilerMetaData(new File("swiftc"), [])
    }

}
