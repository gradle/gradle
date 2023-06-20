/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.featurelifecycle;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.deprecation.DeprecatedFeatureUsage;
import org.gradle.problems.ProblemDiagnostics;

import java.util.List;

public class DefaultDeprecatedUsageProgressDetails implements DeprecatedUsageProgressDetails {

    @VisibleForTesting
    public final DeprecatedFeatureUsage featureUsage;
    private final ProblemDiagnostics diagnostics;

    public DefaultDeprecatedUsageProgressDetails(DeprecatedFeatureUsage featureUsage, ProblemDiagnostics diagnostics) {
        this.featureUsage = featureUsage;
        this.diagnostics = diagnostics;
    }

    @Override
    public String getSummary() {
        return featureUsage.getSummary();
    }

    @Override
    public String getRemovalDetails() {
        return featureUsage.getRemovalDetails();
    }

    @Override
    public String getAdvice() {
        return featureUsage.getAdvice();
    }

    @Override
    public String getContextualAdvice() {
        return featureUsage.getContextualAdvice();
    }

    @Override
    public String getDocumentationUrl() {
        return featureUsage.getDocumentationUrl().documentationUrl();
    }

    @Override
    public String getType() {
        return featureUsage.getType().name();
    }

    @Override
    public List<StackTraceElement> getStackTrace() {
        return diagnostics.getStack();
    }
}
