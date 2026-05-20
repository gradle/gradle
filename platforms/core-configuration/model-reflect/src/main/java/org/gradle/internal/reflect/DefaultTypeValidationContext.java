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

package org.gradle.internal.reflect;

import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class DefaultTypeValidationContext extends ProblemRecordingTypeValidationContext {
    public static final String MISSING_NORMALIZATION_ANNOTATION = "MISSING_NORMALIZATION_ANNOTATION";
    private final boolean reportCacheabilityProblems;
    private final ImmutableList.Builder<ProblemInternal> errors = ImmutableList.builder();
    private final ImmutableList.Builder<ProblemInternal> warnings = ImmutableList.builder();

    public static DefaultTypeValidationContext withRootType(Class<?> rootType, boolean cacheable, ProblemsInternal problems) {
        return new DefaultTypeValidationContext(rootType, cacheable, problems);
    }

    public static DefaultTypeValidationContext withoutRootType(boolean reportCacheabilityProblems, ProblemsInternal problems) {
        return new DefaultTypeValidationContext(null, reportCacheabilityProblems, problems);
    }

    private DefaultTypeValidationContext(@Nullable Class<?> rootType, boolean reportCacheabilityProblems, ProblemsInternal problems) {
        super(rootType, Optional::empty, problems);
        this.reportCacheabilityProblems = reportCacheabilityProblems;
    }

    public static final ProblemId MISSING_NORMALIZATION_ID = ProblemId.create("missing-normalization-annotation", "Missing normalization", GradleCoreProblemGroup.validation().property());

    public static boolean onlyAffectsCacheableWork(ProblemId id) {
        return MISSING_NORMALIZATION_ID.equals(id);
    }


    @Override
    protected void recordError(ProblemInternal problem) {
        if (onlyAffectsCacheableWork(problem.getDefinition().getId()) && !reportCacheabilityProblems) {
            return;
        }
        errors.add(problem);
    }

    @Override
    protected void recordWarning(ProblemInternal problem) {
        warnings.add(problem);
    }

    public ImmutableList<ProblemInternal> getErrors() {
        return errors.build();
    }

    public ImmutableList<ProblemInternal> getWarnings() {
        return warnings.build();
    }
}
