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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationProblem;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;

import javax.annotation.Nullable;

public class DefaultTypeValidationContext extends ProblemRecordingTypeValidationContext {
    private final boolean reportCacheabilityProblems;
    private final ImmutableMap.Builder<String, Severity> problems = ImmutableMap.builder();

    public static DefaultTypeValidationContext withRootType(DocumentationRegistry documentationRegistry, Class<?> rootType, boolean cacheable) {
        return new DefaultTypeValidationContext(documentationRegistry, rootType, cacheable);
    }

    public static DefaultTypeValidationContext withoutRootType(DocumentationRegistry documentationRegistry, boolean reportCacheabilityProblems) {
        return new DefaultTypeValidationContext(documentationRegistry, null, reportCacheabilityProblems);
    }

    private DefaultTypeValidationContext(DocumentationRegistry documentationRegistry, @Nullable Class<?> rootType, boolean reportCacheabilityProblems) {
        super(documentationRegistry, rootType, null);
        this.reportCacheabilityProblems = reportCacheabilityProblems;
    }

    @Override
    protected void recordProblem(TypeValidationProblem problem) {
        boolean onlyAffectsCacheableWork = problem.isOnlyAffectsCacheableWork();
        if (onlyAffectsCacheableWork && !reportCacheabilityProblems) {
            return;
        }
        problems.put(TypeValidationProblemRenderer.renderMinimalInformationAbout(problem), problem.getSeverity());
    }

    public ImmutableMap<String, Severity> getProblems() {
        return problems.build();
    }


}
