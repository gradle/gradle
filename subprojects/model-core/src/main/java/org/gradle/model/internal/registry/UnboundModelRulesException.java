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

package org.gradle.model.internal.registry;

import org.gradle.api.GradleException;
import org.gradle.model.internal.report.unbound.UnboundRule;
import org.gradle.model.internal.report.unbound.UnboundRulesReporter;

import java.io.PrintWriter;
import java.io.StringWriter;

public class UnboundModelRulesException extends GradleException {

    public UnboundModelRulesException(Iterable<? extends UnboundRule> rules) {
        super(toMessage(rules));
    }

    private static String toMessage(Iterable<? extends UnboundRule> rules) {
        StringWriter string = new StringWriter();
        PrintWriter writer = new PrintWriter(string);
        writer.println("The following model rules are unbound:");
        new UnboundRulesReporter(writer, "  ").reportOn(rules);
        return string.toString();
    }
}
