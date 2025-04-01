/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jacoco.rules;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.jacoco.rules.JacocoLimitImpl.SerializableJacocoLimit;
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.List;

public abstract class JacocoViolationRuleImpl implements JacocoViolationRule {

    private final ObjectFactory objectFactory;
    private final ListProperty<JacocoLimit> limits;

    @Inject
    public JacocoViolationRuleImpl(ObjectFactory objectFactory) {
        getEnabled().convention(true);
        getElement().convention("BUNDLE");
        getIncludes().convention(ImmutableList.of("*"));
        this.objectFactory = objectFactory;
        this.limits = objectFactory.listProperty(JacocoLimit.class);
    }

    @Override
    public abstract Property<Boolean> getEnabled();

    @Override
    public abstract Property<String> getElement();

    @Override
    public abstract ListProperty<String> getIncludes();

    @Override
    public abstract ListProperty<String> getExcludes();

    @Override
    public Provider<List<JacocoLimit>> getLimits() {
        // Make it read-only
        return limits.map(SerializableLambdas.transformer(__ -> __));
    }

    @Override
    public JacocoLimit limit(Action<? super JacocoLimit> configureAction) {
        JacocoLimit limit = objectFactory.newInstance(JacocoLimitImpl.class);
        configureAction.execute(limit);
        limits.add(limit);
        return limit;
    }

    @NullMarked
    public static class SerializableJacocoViolationRule implements Serializable {
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
}
