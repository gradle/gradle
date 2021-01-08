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

public class ImmutableModuleSources implements ModuleSources {
    private static final ImmutableModuleSources EMPTY = new ImmutableModuleSources(null, null);

    private final ImmutableModuleSources previous;
    private final ModuleSource value;
    private final int hashCode;
    private final int size;

    public static ImmutableModuleSources of() {
        return EMPTY;
    }

    public static ImmutableModuleSources of(ModuleSource... sources) {
        ImmutableModuleSources cur = EMPTY;
        for (ModuleSource source : sources) {
            cur = new ImmutableModuleSources(cur, source);
        }
        return cur;
    }

    private ImmutableModuleSources(@Nullable ImmutableModuleSources previous, @Nullable ModuleSource value) {
        if (previous != null && value == null) {
            throw new AssertionError("value must not be null");
        }
        this.previous = previous;
        this.value = value;
        this.hashCode = 31 * (previous == null ? 0 : previous.hashCode) + (value == null ? 0 : value.hashCode());
        this.size = previous == null ? 0 : 1 + previous.size;
    }

    public static ImmutableModuleSources of(ModuleSources sources, ModuleSource source) {
        if (sources instanceof ImmutableModuleSources) {
            return new ImmutableModuleSources((ImmutableModuleSources) sources, source);
        }
        List<ModuleSource> all = Lists.newArrayList();
        sources.withSources(all::add);
        all.add(source);
        return of(all.toArray(new ModuleSource[0]));
    }

    public static ImmutableModuleSources of(ModuleSources sources) {
        if (sources instanceof ImmutableModuleSources) {
            return (ImmutableModuleSources) sources;
        }
        return ((MutableModuleSources) sources).asImmutable();
    }

    @Override
    public <T extends ModuleSource> Optional<T> getSource(Class<T> sourceType) {
        if (previous == null) {
            return Optional.empty();
        }
        if (sourceType.isAssignableFrom(value.getClass())) {
            T src = Cast.uncheckedCast(value);
            return Optional.of(src);
        }
        return previous.getSource(sourceType);
    }

    @Override
    public void withSources(Consumer<ModuleSource> consumer) {
        ImmutableModuleSources cur = this;
        while (cur.value != null) {
            consumer.accept(cur.value);
            cur = cur.previous;
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableModuleSources that = (ImmutableModuleSources) o;

        if (!Objects.equals(previous, that.previous)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{Sources = [");
        withSources(src -> sb.append(src).append(","));
        sb.append("]}");
        return sb.toString();
    }
}
