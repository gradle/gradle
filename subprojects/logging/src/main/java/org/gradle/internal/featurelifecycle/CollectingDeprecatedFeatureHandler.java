/*
 * Copyright 2017 the original author or authors.
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

import java.util.HashSet;
import java.util.Set;

public class CollectingDeprecatedFeatureHandler implements FeatureHandler {
    private final LoggingDeprecatedFeatureHandler delegate;
    private final Set<FeatureInvocationSource> invocations = new HashSet<FeatureInvocationSource>();

    public CollectingDeprecatedFeatureHandler(LoggingDeprecatedFeatureHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void init(UsageLocationReporter reporter) {
        delegate.init(reporter);
    }

    @Override
    public void featureUsed(FeatureUsage usage) {
        usage = usage.withStackTrace();
        FeatureInvocationSource invocation = StacktraceAnalyzer.analyzeInvocation(usage);
        invocations.add(invocation);
        if (!invocation.isFromThirdPartyPlugin() || invocation.isUnknown()) {
            delegate.featureUsed(usage);
        }
    }
}
