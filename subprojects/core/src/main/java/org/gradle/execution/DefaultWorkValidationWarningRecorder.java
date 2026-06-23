/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.execution;

import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.internal.execution.Identity;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationUtils;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.problems.internal.rendering.ProblemWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultWorkValidationWarningRecorder implements ValidateStep.ValidationWarningRecorder, WorkValidationWarningReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkValidationWarningRecorder.class);

    // TODO If we can ensure that the recorder is only called once per work execution, then we can demote this to a simple counter
    private final Set<Identity> workWithWarnings = ConcurrentHashMap.newKeySet();

    @Override
    public void recordValidationWarnings(Identity identity, UnitOfWork work, Collection<? extends ProblemInternal> warnings) {
        workWithWarnings.add(identity);

        List<ProblemInternal> uniqueWarnings = WorkValidationUtils.deduplicateAndTruncate(new ArrayList<>(warnings));

        StringWriter rendered = new StringWriter();
        ProblemWriter.simple().write(uniqueWarnings, rendered);
        LOGGER.warn("Execution optimizations have been disabled for {} to ensure correctness due to the following reasons:\n{}",
            work.getDisplayName(), rendered);
        WorkValidationUtils.reportAsDeprecation(uniqueWarnings);
    }

    @Override
    public void reportWorkValidationWarningsAtEndOfBuild() {
        int numberOfUnitsWithWarnings = workWithWarnings.size();
        workWithWarnings.clear();
        if (numberOfUnitsWithWarnings > 0) {
            LOGGER.warn(
                "\nExecution optimizations have been disabled for {} invalid unit(s) of work during this build to ensure correctness." +
                    "\nPlease consult deprecation warnings for more details.",
                numberOfUnitsWithWarnings
            );
        }
    }
}
