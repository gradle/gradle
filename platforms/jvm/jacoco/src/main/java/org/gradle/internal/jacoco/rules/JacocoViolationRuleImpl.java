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
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JacocoViolationRuleImpl implements JacocoViolationRule {

    private boolean enabled = true;
    private String scope = "BUNDLE";
    private List<String> includes = ImmutableList.of("*");
    private List<String> excludes = ImmutableList.of();
    private final List<JacocoLimit> limits = new ArrayList<>();

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void setElement(String element) {
        this.scope = element;
    }

    @Override
    public String getElement() {
        return scope;
    }

    @Override
    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    @Override
    public List<String> getIncludes() {
        return Collections.unmodifiableList(includes);
    }

    @Override
    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    @Override
    public List<String> getExcludes() {
        return Collections.unmodifiableList(excludes);
    }

    @Override
    public List<JacocoLimit> getLimits() {
        return Collections.unmodifiableList(limits);
    }

    @Override
    public JacocoLimit limit(Action<? super JacocoLimit> configureAction) {
        JacocoLimit limit = new JacocoLimitImpl();
        configureAction.execute(limit);
        limits.add(limit);
        return limit;
    }
}
