/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class LoggingExcludeFactory extends DelegatingExcludeFactory {
    private final static Logger LOGGER = LoggerFactory.getLogger(LoggingExcludeFactory.class);

    private final Subject subject;

    LoggingExcludeFactory(ExcludeFactory delegate) {
        super(delegate);
        this.subject = computeWhatToLog();
    }

    private static Subject computeWhatToLog() {
        String subjectString = System.getProperty("org.gradle.internal.dm.trace.excludes", Subject.all.toString());
        return Subject.valueOf(subjectString.toLowerCase(Locale.ROOT));
    }

    public static ExcludeFactory maybeLog(ExcludeFactory factory) {
        if (LOGGER.isDebugEnabled()) {
            return new LoggingExcludeFactory(factory);
        }
        return factory;
    }

    @Override
    public ExcludeSpec anyOf(ExcludeSpec one, ExcludeSpec two) {
        return log("anyOf", () -> super.anyOf(one, two), one, two);
    }

    @Override
    public ExcludeSpec allOf(ExcludeSpec one, ExcludeSpec two) {
        return log("allOf", () -> super.allOf(one, two), one, two);
    }

    @Override
    public ExcludeSpec anyOf(Set<ExcludeSpec> specs) {
        return log("anyOf", () -> super.anyOf(specs), specs);
    }

    @Override
    public ExcludeSpec allOf(Set<ExcludeSpec> specs) {
        return log("allOf", () -> super.allOf(specs), specs);
    }

    private ExcludeSpec log(String operationName, Factory<ExcludeSpec> factory, Object... operands) {
        ExcludeSpec spec;
        try {
            spec = factory.create();
        } catch (StackOverflowError e) {
            if (subject.isTraceStackOverflows()) {
                StringWriter sw = new StringWriter();
                sw.append("{\"stackoverflow\": [");
                PrintWriter printWriter = new PrintWriter(sw);
                StackTraceElement[] stackTrace = e.getStackTrace();
                printWriter.print(Arrays.stream(stackTrace)
                    .limit(100)
                    .map(d -> "\"" + d.toString() + "\"")
                    .collect(Collectors.joining(", "))
                );
                sw.append("]}");
                LOGGER.debug("{\"operation\": { \"name\": \"{}\", \"operands\": {}, \"result\": {} } }", operationName, toList(operands), sw.toString());
            }
            throw UncheckedException.throwAsUncheckedException(e);
        }
        if (subject.isTraceOperations()) {
            LOGGER.debug("{\"operation\": { \"name\": \"{}\", \"operands\": {}, \"result\": {} } }", operationName, toList(operands), spec);
        }
        return spec;
    }

    private static Collection<?> toList(Object[] operands) {
        return singleCollection(operands) ? (Collection<?>) operands[0] : Arrays.asList(operands);
    }

    private static boolean singleCollection(Object[] operands) {
        return operands.length== 1 && operands[0] instanceof Collection;
    }

    private enum Subject {
        all(true, true),
        stackoverflow(false, true),
        operations(true, false);

        private final boolean traceOperations;
        private final boolean traceStackOverflows;

        Subject(boolean traceOperations, boolean traceStackOverflows) {
            this.traceOperations = traceOperations;
            this.traceStackOverflows = traceStackOverflows;
        }

        public boolean isTraceOperations() {
            return traceOperations;
        }

        public boolean isTraceStackOverflows() {
            return traceStackOverflows;
        }
    }
}
