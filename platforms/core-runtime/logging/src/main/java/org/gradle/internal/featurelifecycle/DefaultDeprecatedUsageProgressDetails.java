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
import org.gradle.api.problems.DocLink;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.deprecation.DeprecatedFeatureUsage;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.problems.ProblemDiagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultDeprecatedUsageProgressDetails implements DeprecatedUsageProgressDetails, CustomOperationTraceSerialization {

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
        DocLink documentationUrl = featureUsage.getDocumentationUrl();
        return documentationUrl == null ? null : documentationUrl.getUrl();
    }

    @Override
    public String getType() {
        return featureUsage.getType().name();
    }

    @Override
    public List<StackTraceElement> getStackTrace() {
        return diagnostics.getStack();
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        Map<String, Object> deprecation = new LinkedHashMap<String, Object>();
        deprecation.put("summary", getSummary());
        deprecation.put("removalDetails", getRemovalDetails());
        deprecation.put("advice", getAdvice());
        deprecation.put("contextualAdvice", getContextualAdvice());
        deprecation.put("documentationUrl", getDocumentationUrl());
        deprecation.put("type", getType());
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement ste : getStackTrace()) {
            sb.append(ste.toString());
            sb.append(SystemProperties.getInstance().getLineSeparator());
        }
        deprecation.put("stackTrace", sb.toString());
        // the properties are wrapped to an enclosing map to improve the readability of the trace files
        return Collections.singletonMap("deprecation", deprecation);
    }
}
