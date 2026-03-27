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
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.model.RuleSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;

@ThreadSafe
@ServiceScope(Scope.Global.class)
public class ModelRuleSourceDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRuleSourceDetector.class);

    private static final Comparator<Class<?>> COMPARE_BY_CLASS_NAME = Comparator.comparing(Class::getName);

    final LoadingCache<Class<?>, Collection<Reference<Class<? extends RuleSource>>>> cache = CacheBuilder.newBuilder()
        .weakKeys()
        .build(new CacheLoader<Class<?>, Collection<Reference<Class<? extends RuleSource>>>>() {
            @Override
            public Collection<Reference<Class<? extends RuleSource>>> load(@SuppressWarnings("NullableProblems") Class<?> container) {
                if (isRuleSource(container)) {
                    Class<? extends RuleSource> castClass = Cast.uncheckedCast(container);
                    return ImmutableSet.of(new WeakReference<>(castClass));
                }

                Class<?>[] declaredClasses = declaredClassesOf(container);
                if (declaredClasses.length == 0) {
                    return Collections.emptySet();
                }

                Class<?>[] sortedDeclaredClasses = new Class<?>[declaredClasses.length];
                System.arraycopy(declaredClasses, 0, sortedDeclaredClasses, 0, declaredClasses.length);
                Arrays.sort(sortedDeclaredClasses, COMPARE_BY_CLASS_NAME);

                ImmutableList.Builder<Reference<Class<? extends RuleSource>>> found = ImmutableList.builder();
                for (Class<?> declaredClass : sortedDeclaredClasses) {
                    if (isRuleSource(declaredClass)) {
                        Class<? extends RuleSource> castClass = Cast.uncheckedCast(declaredClass);
                        found.add(new WeakReference<>(castClass));
                    }
                }

                return found.build();
            }
        });

    // TODO return a richer data structure that provides meta data about how the source was found, for use is diagnostics
    public Iterable<Class<? extends RuleSource>> getDeclaredSources(Class<?> container) {
        try {
            return FluentIterable.from(cache.get(container))
                .transform((Function<Reference<Class<? extends RuleSource>>, Class<? extends RuleSource>>) Reference::get)
                .filter(Predicates.notNull());
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public boolean hasRules(Class<?> container) {
        return !Iterables.isEmpty(getDeclaredSources(container));
    }

    private static boolean isRuleSource(Class<?> clazz) {
        try {
            return RuleSource.class.isAssignableFrom(clazz);
        } catch (LinkageError e) {
            LOGGER.debug("Could not inspect class {}, skipping rule source detection", clazz.getName(), e);
            return false;
        }
    }

    private static Class<?>[] declaredClassesOf(Class<?> clazz) {
        try {
            return clazz.getDeclaredClasses();
        } catch (LinkageError e) {
            LOGGER.debug("Could not load declared classes of {}, skipping rule source detection", clazz.getName(), e);
            return new Class<?>[0];
        }
    }
}
