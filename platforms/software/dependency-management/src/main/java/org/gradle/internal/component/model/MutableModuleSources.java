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

import com.google.common.collect.Lists;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class MutableModuleSources implements ModuleSources {
    private List<ModuleSource> moduleSources;

    public static MutableModuleSources of(@Nullable ModuleSource source) {
        MutableModuleSources sources = new MutableModuleSources();
        if (source != null) {
            sources.add(source);
        }
        return sources;
    }

    public static MutableModuleSources of(ModuleSources sources) {
        if (sources instanceof MutableModuleSources) {
            return (MutableModuleSources) sources;
        }
        MutableModuleSources mutableModuleSources = new MutableModuleSources();
        if (sources == null) {
            return mutableModuleSources;
        }
        sources.withSources(mutableModuleSources::add);
        return mutableModuleSources;
    }

    @Override
    public <T extends ModuleSource> Optional<T> getSource(Class<T> sourceType) {
        if (moduleSources == null) {
            return Optional.empty();
        }
        return Cast.uncheckedCast(moduleSources.stream()
            .filter(src -> sourceType.isAssignableFrom(src.getClass()))
            .findFirst());
    }

    @Override
    public void withSources(Consumer<ModuleSource> consumer) {
        if (moduleSources != null) {
            moduleSources.forEach(consumer);
        }
    }

    @Override
    public int size() {
        return moduleSources == null ? 0 : moduleSources.size();
    }

    public void add(ModuleSource source) {
        assert source != null;
        maybeCreateStore();
        moduleSources.add(source);
    }

    private void maybeCreateStore() {
        if (moduleSources == null) {
            moduleSources = Lists.newArrayListWithExpectedSize(2);
        }
    }

    ImmutableModuleSources asImmutable() {
        if (moduleSources == null) {
            return ImmutableModuleSources.of();
        }
        return ImmutableModuleSources.of(moduleSources.toArray(new ModuleSource[0]));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MutableModuleSources that = (MutableModuleSources) o;

        return Objects.equals(moduleSources, that.moduleSources);
    }

    @Override
    public int hashCode() {
        return moduleSources != null ? moduleSources.hashCode() : 0;
    }
}
