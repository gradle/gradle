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

import javax.annotation.Nullable;

public class DefaultTypeValidationContext extends MessageFormattingTypeValidationContext {
    private final boolean cacheable;
    private final ImmutableMap.Builder<String, Severity> problems = ImmutableMap.builder();

    public static DefaultTypeValidationContext withRootType(Class<?> rootType, boolean cacheable) {
        return new DefaultTypeValidationContext(rootType, cacheable);
    }

    public static DefaultTypeValidationContext withoutRootType(boolean cacheable) {
        return new DefaultTypeValidationContext(null, cacheable);
    }

    private DefaultTypeValidationContext(@Nullable Class<?> rootType, boolean cacheable) {
        super(rootType);
        this.cacheable = cacheable;
    }

    @Override
    protected void recordProblem(Severity severity, String message) {
        if (severity == Severity.CACHEABILITY_WARNING && !cacheable) {
            return;
        }
        problems.put(message, severity.toReportableSeverity());
    }

    public ImmutableMap<String, Severity> getProblems() {
        return problems.build();
    }
}
