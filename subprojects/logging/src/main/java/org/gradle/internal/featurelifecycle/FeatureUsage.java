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

package org.gradle.internal.featurelifecycle;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable description of the usage of a deprecated feature.
 */
public class FeatureUsage {
    private final String summary;
    private final String removalDetails;
    private final String advice;
    private final FeatureUsageType type;
    private final Class<?> calledFrom;
    private final Exception traceException;
    private final String contextualAdvice;

    private List<StackTraceElement> stack;

    public enum FeatureUsageType {
        /**
         * The feature usage in user code directly leads to a deprecation warning.
         * The stacktrace in that feature usage directly points to the user code
         * using the feature.
         */
        USER_CODE_DIRECT,

        /**
         * The feature usage in user code indirectly leads to a deprecation warning as
         * the usage was detect later in the build.  The stacktrace in that
         * feature usage does not directly point to the user code using the feature.
         */
        USER_CODE_INDIRECT,

        /**
         * A feature / functionality on how gradle is invoked is used.
         * This can be typically a no longer supported java version or the use of
         * a deprecated or incubating command line flag.
         */
        BUILD_INVOCATION
    }

    public FeatureUsage(String summary, String removalDetails, String advice, String contextualAdvice, FeatureUsageType usageType, Class<?> calledFrom) {
        this.summary = summary;
        this.removalDetails = removalDetails;
        this.advice = advice;
        this.contextualAdvice = contextualAdvice;
        this.type = usageType;
        this.calledFrom = calledFrom;
        this.traceException = new Exception();
    }

    @VisibleForTesting
    FeatureUsage(FeatureUsage usage, Exception traceException) {
        this.summary = usage.summary;
        this.removalDetails = usage.removalDetails;
        this.advice = usage.advice;
        this.contextualAdvice = usage.contextualAdvice;
        this.type = usage.type;
        this.calledFrom = usage.calledFrom;
        this.traceException = Preconditions.checkNotNull(traceException);
    }

    public String getSummary() {
        return summary;
    }

    public String getRemovalDetails() {
        return removalDetails;
    }

    public String getAdvice() {
        return advice;
    }

    public String getContextualAdvice() {
        return contextualAdvice;
    }

    public FeatureUsageType getType() {
        return type;
    }

    public List<StackTraceElement> getStack() {
        if (stack == null) {
            stack = calculateStack(calledFrom, traceException);
        }
        return stack;
    }

    private static List<StackTraceElement> calculateStack(Class<?> calledFrom, Exception traceRoot) {
        StackTraceElement[] originalStack = traceRoot.getStackTrace();
        final String calledFromName = calledFrom.getName();
        boolean calledFromFound = false;
        int caller;
        for (caller = 0; caller < originalStack.length; caller++) {
            StackTraceElement current = originalStack[caller];
            if (!calledFromFound) {
                if (current.getClassName().startsWith(calledFromName)) {
                    calledFromFound = true;
                }
            } else {
                if (!current.getClassName().startsWith(calledFromName)) {
                    break;
                }
            }
        }

        caller = skipSystemStackElements(originalStack, caller);
        List<StackTraceElement> result = new ArrayList<StackTraceElement>();
        for (; caller < originalStack.length; caller++) {
            result.add(originalStack[caller]);
        }
        return result;
    }

    private static int skipSystemStackElements(StackTraceElement[] stackTrace, int caller) {
        for (; caller < stackTrace.length; caller++) {
            String currentClassName = stackTrace[caller].getClassName();
            if (!currentClassName.startsWith("org.codehaus.groovy.")
                && !currentClassName.startsWith("org.gradle.internal.metaobject.")
                && !currentClassName.startsWith("groovy.")
                && !currentClassName.startsWith("java.")
                && !currentClassName.startsWith("jdk.internal.")
                ) {
                break;
            }
        }
        return caller;
    }

    public String formattedMessage() {
        StringBuilder outputBuilder = new StringBuilder(summary);
        append(outputBuilder, removalDetails);
        append(outputBuilder, contextualAdvice);
        append(outputBuilder, advice);
        return outputBuilder.toString();
    }

    private void append(StringBuilder outputBuilder, String removalDetails) {
        if (!StringUtils.isEmpty(removalDetails)) {
            outputBuilder.append(" ").append(removalDetails);
        }
    }

}
