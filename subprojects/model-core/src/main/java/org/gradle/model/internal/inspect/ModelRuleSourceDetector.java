/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.inspect;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.model.RuleSource;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;

@ThreadSafe
public class ModelRuleSourceDetector {

    private static final Comparator<Class<?>> COMPARE_BY_CLASS_NAME = new Comparator<Class<?>>() {
        public int compare(Class<?> left, Class<?> right) {
            return left.getName().compareTo(right.getName());
        }
    };

    final LoadingCache<Class<?>, Collection<Reference<Class<? extends RuleSource>>>> cache = CacheBuilder.newBuilder()
            .weakKeys()
            .build(new CacheLoader<Class<?>, Collection<Reference<Class<? extends RuleSource>>>>() {
                @Override
                public Collection<Reference<Class<? extends RuleSource>>> load(@SuppressWarnings("NullableProblems") Class<?> container) throws Exception {
                    if (isRuleSource(container)) {
                        Class<? extends RuleSource> castClass = Cast.uncheckedCast(container);
                        return ImmutableSet.<Reference<Class<? extends RuleSource>>>of(new WeakReference<Class<? extends RuleSource>>(castClass));
                    }

                    Class<?>[] declaredClasses = container.getDeclaredClasses();

                    if (declaredClasses.length == 0) {
                        return Collections.emptySet();
                    } else {
                        Class<?>[] sortedDeclaredClasses = new Class<?>[declaredClasses.length];
                        System.arraycopy(declaredClasses, 0, sortedDeclaredClasses, 0, declaredClasses.length);
                        Arrays.sort(sortedDeclaredClasses, COMPARE_BY_CLASS_NAME);

                        ImmutableList.Builder<Reference<Class<? extends RuleSource>>> found = ImmutableList.builder();
                        for (Class<?> declaredClass : sortedDeclaredClasses) {
                            if (isRuleSource(declaredClass)) {
                                Class<? extends RuleSource> castClass = Cast.uncheckedCast(declaredClass);
                                found.add(new WeakReference<Class<? extends RuleSource>>(castClass));
                            }
                        }

                        return found.build();
                    }
                }
            });

    // TODO return a richer data structure that provides meta data about how the source was found, for use is diagnostics
    public Iterable<Class<? extends RuleSource>> getDeclaredSources(Class<?> container) {
        try {
            return FluentIterable.from(cache.get(container))
                    .transform(new Function<Reference<Class<? extends RuleSource>>, Class<? extends RuleSource>>() {
                        @Override
                        public Class<? extends RuleSource> apply(Reference<Class<? extends RuleSource>> input) {
                            return input.get();
                        }
                    })
                    .filter(Predicates.notNull());
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public boolean hasRules(Class<?> container) {
        return !Iterables.isEmpty(getDeclaredSources(container));
    }

    private boolean isRuleSource(Class<?> clazz) {
        return RuleSource.class.isAssignableFrom(clazz);
    }
}
