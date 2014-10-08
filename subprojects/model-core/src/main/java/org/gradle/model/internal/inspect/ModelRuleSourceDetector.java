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

import com.google.common.collect.ImmutableSet;
import org.gradle.model.RuleSource;

import java.util.Collections;
import java.util.Set;

public class ModelRuleSourceDetector {

    // TODO return a richer data structure that provides meta data about how the source was found, for use is diagnostics
    public Set<Class<?>> getDeclaredSources(Class<?> container) {
        if (container.isAnnotationPresent(RuleSource.class)) {
            return ImmutableSet.<Class<?>>of(container);
        }
        Class<?>[] declaredClasses = container.getDeclaredClasses();
        if (declaredClasses.length == 0) {
            return Collections.emptySet();
        } else {
            ImmutableSet.Builder<Class<?>> found = ImmutableSet.builder();
            for (Class<?> declaredClass : declaredClasses) {
                if (declaredClass.isAnnotationPresent(RuleSource.class)) {
                    found.add(declaredClass);
                }
            }

            return found.build();
        }
    }

}
