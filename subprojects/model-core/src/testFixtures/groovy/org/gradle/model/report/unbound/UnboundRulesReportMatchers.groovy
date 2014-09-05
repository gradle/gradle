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

package org.gradle.model.report.unbound

import org.gradle.model.internal.report.unbound.UnboundRule
import org.gradle.model.internal.report.unbound.UnboundRulesReporter
import org.hamcrest.Matcher

import static org.gradle.util.Matchers.normalizedLineSeparators
import static org.gradle.util.TextUtil.normaliseLineSeparators
import static org.hamcrest.Matchers.equalTo

class UnboundRulesReportMatchers {

    static Matcher<String> unbound(UnboundRule.Builder... rules) {
        def string = new StringWriter()
        def writer = new PrintWriter(string)
        writer.println("The following model rules are unbound:")
        def reporter = new UnboundRulesReporter(writer, "  ")
        reporter.reportOn(rules.toList()*.build())

        normalizedLineSeparators(equalTo(normaliseLineSeparators(string.toString())))
    }
}
