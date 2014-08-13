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

package org.gradle.model.internal.registry

import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.report.UnboundRuleReportOutputBuilder
import org.gradle.util.TextUtil
import spock.lang.Specification

class UnboundRuleReportOutputBuilderTest extends Specification {

    def output = new StringWriter()
    def builder = new UnboundRuleReportOutputBuilder(new PrintWriter(output), "> ")

    def "builds output"() {
        when:
        builder.rule(new SimpleModelRuleDescriptor("r1"))
                .mutableUnbound("parent.p1", String.name)
                .mutableBound("parent.p2", Integer.name)
                .immutableUnbound("parent.p3", Number.name)
                .immutableUnbound(null, Number.name)
                .immutableBound("parent.p5", Number.name)

        then:
        output.toString() == TextUtil.normaliseLineSeparators("""> r1
>   Mutable:
>     - parent.p1 (java.lang.String)
>     + parent.p2 (java.lang.Integer)
>   Immutable:
>     - parent.p3 (java.lang.Number)
>     - <unspecified> (java.lang.Number)
>     + parent.p5 (java.lang.Number)""")
    }

}
