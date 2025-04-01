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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;

import javax.inject.Inject;
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
        return limits.map(__ -> __);
    }

    @Override
    public JacocoLimit limit(Action<? super JacocoLimit> configureAction) {
        JacocoLimit limit = objectFactory.newInstance(JacocoLimitImpl.class);
        configureAction.execute(limit);
        limits.add(limit);
        return limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JacocoViolationRuleImpl that = (JacocoViolationRuleImpl) o;

        if (getEnabled().get().equals(that.getEnabled().get())) {
            return false;
        }
        if (getElement().get().equals(that.getElement().get())) {
            return false;
        }
        if (getIncludes().get().equals(that.getIncludes().get())) {
            return false;
        }
        if (getExcludes().get().equals(that.getExcludes().get())) {
            return false;
        }
        return getLimits().get().equals(that.getLimits().get());
    }

    @Override
    public int hashCode() {
        int result = getEnabled().get() ? 1 : 0;
        result = 31 * result + getElement().get().hashCode();
        result = 31 * result + getIncludes().get().hashCode();
        result = 31 * result + getExcludes().get().hashCode();
        result = 31 * result + getLimits().get().hashCode();
        return result;
    }
}
