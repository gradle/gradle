/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.problems;

import com.google.common.collect.ImmutableList;
import org.gradle.problems.Location;
import org.gradle.problems.ProblemDiagnostics;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;
import org.gradle.problems.buildtree.ProblemLocationAnalyzer;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultProblemDiagnosticsFactory implements ProblemDiagnosticsFactory {
    private static final StackTraceTransformer NO_OP = ImmutableList::copyOf;
    private final ProblemLocationAnalyzer locationAnalyzer;

    public DefaultProblemDiagnosticsFactory(ProblemLocationAnalyzer locationAnalyzer) {
        this.locationAnalyzer = locationAnalyzer;
    }

    @Override
    public ProblemDiagnostics forCurrentCaller(@Nullable Throwable exception) {
        if (exception == null) {
            return locationFromStackTrace(new Exception(), false, NO_OP);
        } else {
            return locationFromStackTrace(exception, true, NO_OP);
        }
    }

    @Override
    public ProblemDiagnostics forCurrentCaller(StackTraceTransformer transformer) {
        return locationFromStackTrace(new Exception(), false, transformer);
    }

    private ProblemDiagnostics locationFromStackTrace(Throwable throwable, boolean fromException, StackTraceTransformer transformer) {
        List<StackTraceElement> stackTrace = transformer.transform(throwable.getStackTrace());
        Location location = locationAnalyzer.locationForUsage(stackTrace, fromException);
        return new ProblemDiagnostics() {
            @Nullable
            @Override
            public Throwable getException() {
                return fromException ? throwable : null;
            }

            @Override
            public List<StackTraceElement> getStack() {
                return stackTrace;
            }

            @Nullable
            @Override
            public Location getLocation() {
                return location;
            }
        };
    }
}
