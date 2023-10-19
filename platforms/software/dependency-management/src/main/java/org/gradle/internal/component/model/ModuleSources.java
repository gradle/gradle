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
package org.gradle.internal.component.model;

import org.gradle.internal.Cast;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ModuleSources {
    <T extends ModuleSource> Optional<T> getSource(Class<T> sourceType);

    void withSources(Consumer<ModuleSource> consumer);

    /**
     * Executes an action on the first source found of the following type, if any.
     */
    default <T extends ModuleSource, R> R withSource(Class<T> sourceType, Function<Optional<T>, R> action) {
        return action.apply(getSource(sourceType));
    }

    default <T extends ModuleSource> void withSources(Class<T> sourceType, Consumer<T> consumer) {
        withSources(src -> {
            if (sourceType.isAssignableFrom(src.getClass())) {
                consumer.accept(Cast.uncheckedCast(src));
            }
        });
    }

    int size();
}
