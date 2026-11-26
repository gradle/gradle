/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.jacoco;

import com.google.common.collect.ImmutableList;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.List;

/**
 * A serializable snapshot of a {@link JacocoViolationRule}, used to pass rules to the worker.
 */
@NullMarked
public class SerializableJacocoViolationRule implements Serializable {
    private final boolean isEnabled;
    private final String element;
    private final List<String> includes;
    private final List<String> excludes;
    private final List<SerializableJacocoLimit> limits;

    public SerializableJacocoViolationRule(
        boolean isEnabled,
        String element,
        List<String> includes,
        List<String> excludes,
        List<SerializableJacocoLimit> limits
    ) {
        this.isEnabled = isEnabled;
        this.element = element;
        this.includes = includes;
        this.excludes = excludes;
        this.limits = limits;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public String getElement() {
        return element;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public List<SerializableJacocoLimit> getLimits() {
        return limits;
    }

    public static SerializableJacocoViolationRule of(JacocoViolationRule rule) {
        return new SerializableJacocoViolationRule(
            rule.getEnabled().get(),
            rule.getElement().get(),
            rule.getIncludes().get(),
            rule.getExcludes().get(),
            rule.getLimits().get().stream().map(SerializableJacocoLimit::of).collect(ImmutableList.toImmutableList())
        );
    }
}
