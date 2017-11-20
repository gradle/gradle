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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable description of the usage of a deprecated or incubating feature.
 */
public class FeatureUsage {
    public enum FeatureType {
        INCUBATING,
        DEPRECATED
    }

    private final String message;
    private final List<StackTraceElement> stack;
    private final Class<?> calledFrom;
    private final FeatureType type;

    public static FeatureUsage deprecatedFeature(String message, Class<?> calledFrom) {
        return new FeatureUsage(message, calledFrom, FeatureType.DEPRECATED);
    }

    public static FeatureUsage incubatingFeature(String message, Class<?> calledFrom) {
        return new FeatureUsage(message, calledFrom, FeatureType.INCUBATING);
    }

    FeatureUsage(String message, Class<?> calledFrom, FeatureType type) {
        this.message = message;
        this.calledFrom = calledFrom;
        this.stack = Collections.emptyList();
        this.type = type;
    }

    FeatureUsage(FeatureUsage usage, List<StackTraceElement> stack) {
        if (stack == null) {
            throw new NullPointerException("stack is null");
        }
        this.message = usage.message;
        this.calledFrom = usage.calledFrom;
        this.stack = Collections.unmodifiableList(new ArrayList<StackTraceElement>(stack));
        this.type = usage.type;
    }

    public FeatureType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public List<StackTraceElement> getStack() {
        return stack;
    }

    /**
     * Creates a copy of this usage with the stack trace populated. Implementation is a bit limited in that it assumes that this method is called from the same thread that triggered the usage.
     */
    FeatureUsage withStackTrace() {
        if (stack.isEmpty()) {
            return new FeatureUsage(this, StacktraceAnalyzer.getCleansedStackTrace(calledFrom));
        } else {
            return this;
        }
    }
}
