/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.report.unbound

import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class UnboundRulesReporterTest extends Specification {

    def output = new StringWriter()

    def reporter = new UnboundRulesReporter(new PrintWriter(output), "> ")

    def "reports on unbound rules"() {
        when:
        reporter.reportOn([
                UnboundRule.descriptor("r1")
                        .mutableInput(UnboundRuleInput.type(String).path("parent.p1"))
                        .mutableInput(UnboundRuleInput.type(String).scope("some.scope"))
                        .mutableInput(UnboundRuleInput.type(Integer).bound().path("parent.p3"))
                        .immutableInput(UnboundRuleInput.type(Number).path("parent.p4").suggestions("parent.p31", "parent.p32"))
                        .immutableInput(UnboundRuleInput.type(Number))
                        .immutableInput(UnboundRuleInput.type(Number).bound().path("parent.p6")).build()
        ])

        then:
        output.toString() == TextUtil.toPlatformLineSeparators("""> r1
>   subject:
>     - parent.p1 String [*]
>     - <no path> String [*]
>         scope: some.scope
>     - parent.p3 Integer
>   inputs:
>     - parent.p4 Number [*]
>         suggestions: parent.p31, parent.p32
>     - <no path> Number [*]
>     - parent.p6 Number

[*] - indicates that a model item could not be found for the path or type.
""")
    }
}

