/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.function.BiFunction;

public abstract class DefaultJvmTestSuiteTarget implements JvmTestSuiteTarget {
    private final String name;

    @Inject public DefaultJvmTestSuiteTarget(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public TaskProvider<Test> getTestTask() {
        return new TaskProvider<Test>() {
            @Override
            public void configure(Action<? super Test> action) {
                action.execute(getRunTask().get());
            }

            @Override
            public String getName() {
                return getRunTask().get().getName();
            }

            @Override
            public Test get() {
                return getRunTask().get();
            }

            @Nullable
            @Override
            public Test getOrNull() {
                return getRunTask().getOrNull();
            }

            @Override
            public Test getOrElse(Test defaultValue) {
                return getRunTask().getOrElse(defaultValue);
            }

            @Override
            public <S> Provider<S> map(Transformer<? extends S, ? super Test> transformer) {
                return getRunTask().map(transformer);
            }

            @Override
            public <S> Provider<S> flatMap(Transformer<? extends Provider<? extends S>, ? super Test> transformer) {
                return getRunTask().flatMap(transformer);
            }

            @Override
            public boolean isPresent() {
                return getRunTask().isPresent();
            }

            @Override
            public Provider<Test> orElse(Test value) {
                return getRunTask().orElse(value);
            }

            @Override
            public Provider<Test> orElse(Provider<? extends Test> provider) {
                return getRunTask().orElse(provider);
            }

            @Override
            public Provider<Test> forUseAtConfigurationTime() {
                return getRunTask().forUseAtConfigurationTime();
            }

            @Override
            public <B, R> Provider<R> zip(Provider<B> provider, BiFunction<Test, B, R> biFunction) {
                return getRunTask().zip(provider, biFunction);
            }
        };
    }
}
