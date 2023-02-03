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
package org.gradle.internal.action;

import com.google.common.base.Objects;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.internal.DefaultActionConfiguration;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.JavaPropertyReflectionUtil;
import org.gradle.internal.snapshot.impl.IsolatedArray;

import java.util.Arrays;

public class DefaultConfigurableRule<DETAILS> implements ConfigurableRule<DETAILS> {
    private final static Object[] EMPTY_ARRAY = new Object[0];

    private final Class<? extends Action<DETAILS>> rule;
    private final Isolatable<Object[]> ruleParams;
    private final boolean cacheable;

    private DefaultConfigurableRule(Class<? extends Action<DETAILS>> rule, Isolatable<Object[]> ruleParams) {
        this.rule = rule;
        this.ruleParams = ruleParams;
        this.cacheable = hasCacheableAnnotation(rule);
    }

    private static <DETAILS> boolean hasCacheableAnnotation(Class<? extends Action<DETAILS>> rule) {
        return JavaPropertyReflectionUtil.getAnnotation(rule, CacheableRule.class) != null;
    }

    public static <DETAILS> ConfigurableRule<DETAILS> of(Class<? extends Action<DETAILS>> rule) {
        return new DefaultConfigurableRule<DETAILS>(rule, IsolatedArray.EMPTY);
    }

    public static <DETAILS> ConfigurableRule<DETAILS> of(Class<? extends Action<DETAILS>> rule, Action<? super ActionConfiguration> action, IsolatableFactory isolatableFactory) {
        Object[] params = EMPTY_ARRAY;
        if (action != null) {
            ActionConfiguration configuration = new DefaultActionConfiguration();
            action.execute(configuration);
            params = configuration.getParams();
        }
        return new DefaultConfigurableRule<DETAILS>(rule, isolatableFactory.isolate(params));
    }

    @Override
    public Class<? extends Action<DETAILS>> getRuleClass() {
        return rule;
    }

    @Override
    public Isolatable<Object[]> getRuleParams() {
        return ruleParams;
    }

    @Override
    public boolean isCacheable() {
        return cacheable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultConfigurableRule<?> that = (DefaultConfigurableRule<?>) o;
        return cacheable == that.cacheable &&
            Objects.equal(rule, that.rule) &&
            Objects.equal(ruleParams, that.ruleParams);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rule, ruleParams, cacheable);
    }

    @Override
    public String toString() {
        return "DefaultConfigurableRule{" +
            "rule=" + rule +
            ", ruleParams=" + Arrays.toString(ruleParams.isolate()) +
            '}';
    }
}
