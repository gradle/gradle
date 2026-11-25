/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.provider.views.ListPropertyListView;
import org.gradle.api.internal.provider.views.MapPropertyMapView;
import org.gradle.api.internal.provider.views.SetPropertySetView;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Cast;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Setters of eager properties that store their value in a lazy property.
 *
 * <p>A null value is set as an explicit absent value, not with {@code set(null)}.
 * So the eager getter returns null afterwards, also when a convention is set later,
 * and {@code unset()} on the lazy property brings the convention back.</p>
 */
public final class EagerPropertySupport {

    private EagerPropertySupport() {
    }

    public static <T> void setEager(Property<T> property, @Nullable T value) {
        if (value == null) {
            property.set(Providers.<T>notDefined());
        } else {
            property.set(value);
        }
    }

    public static <T> void setEager(ListProperty<T> property, @Nullable Iterable<? extends T> value) {
        if (value == null) {
            property.set(Providers.<List<T>>notDefined());
        } else if (value instanceof ConventionKeepingListView && ((ConventionKeepingListView<?>) value).isViewOf(property)) {
            // The property keeps a live reference to the value, so its own view must be copied
            property.set(new ArrayList<T>(Cast.<ConventionKeepingListView<T>>uncheckedNonnullCast(value)));
        } else {
            property.set(value);
        }
    }

    public static <T> void setEager(SetProperty<T> property, @Nullable Iterable<? extends T> value) {
        if (value == null) {
            property.set(Providers.<Set<T>>notDefined());
        } else if (value instanceof ConventionKeepingSetView && ((ConventionKeepingSetView<?>) value).isViewOf(property)) {
            // The property keeps a live reference to the value, so its own view must be copied
            property.set(new LinkedHashSet<T>(Cast.<ConventionKeepingSetView<T>>uncheckedNonnullCast(value)));
        } else {
            property.set(value);
        }
    }

    /**
     * Adds the elements without reading the property, and keeps the elements that come from the convention.
     */
    public static <T> void appendAll(SetProperty<T> property, Iterable<? extends T> elements) {
        Cast.<CollectionPropertyInternal<T, Set<T>>>uncheckedNonnullCast(property).appendAll(elements);
    }

    public static <K, V> void setEager(MapProperty<K, V> property, @Nullable Map<? extends K, ? extends V> value) {
        if (value == null) {
            property.set(Providers.<Map<K, V>>notDefined());
        } else if (value instanceof ConventionKeepingMapView && ((ConventionKeepingMapView<?, ?>) value).isViewOf(property)) {
            // The property keeps a live reference to the value, so its own view must be copied
            property.set(new LinkedHashMap<K, V>(Cast.<ConventionKeepingMapView<K, V>>uncheckedNonnullCast(value)));
        } else {
            property.set(value);
        }
    }

    /**
     * Returns what an eager file collection getter returned before its storage became lazy:
     * null when no source is declared, the single declared file collection, or the file collection
     * that a convention mapping returns. Otherwise returns the lazy collection itself.
     */
    @Nullable
    public static FileCollection getEager(ConfigurableFileCollection files) {
        Set<Object> sources = files.getFrom();
        if (sources.isEmpty()) {
            return null;
        }
        if (sources.size() == 1) {
            Object source = sources.iterator().next();
            if (source instanceof FileCollection) {
                return (FileCollection) source;
            }
            if (source instanceof FileCollectionConventionMapping) {
                Object value = ((FileCollectionConventionMapping) source).call();
                if (value == null || value instanceof FileCollection) {
                    return (FileCollection) value;
                }
            }
        }
        return files;
    }

    /**
     * A write-through view whose additions keep the entries that come from the convention.
     */
    public static <K, V> Map<K, V> conventionKeepingView(MapProperty<K, V> property) {
        return new ConventionKeepingMapView<>(property);
    }

    /**
     * A write-through view whose additions keep the elements that come from the convention.
     */
    public static <T> List<T> conventionKeepingView(ListProperty<T> property) {
        return new ConventionKeepingListView<>(property);
    }

    /**
     * A write-through view whose additions keep the elements that come from the convention.
     */
    public static <T> Set<T> conventionKeepingView(SetProperty<T> property) {
        return new ConventionKeepingSetView<>(property);
    }

    /**
     * The view of {@link #conventionKeepingView(SetProperty)}, or null when the property has no value,
     * which an eager {@code setX(null)} leaves behind.
     */
    @Nullable
    public static <T> Set<T> conventionKeepingViewOrNull(SetProperty<T> property) {
        return property.isPresent() ? conventionKeepingView(property) : null;
    }

    private static class ConventionKeepingSetView<T> extends SetPropertySetView<T> {
        private final CollectionPropertyInternal<T, Set<T>> property;

        ConventionKeepingSetView(SetProperty<T> property) {
            super(property);
            this.property = Cast.uncheckedNonnullCast(property);
        }

        boolean isViewOf(Object other) {
            return property == other;
        }

        @Override
        protected void addElement(T element) {
            property.append(element);
        }
    }

    private static class ConventionKeepingMapView<K, V> extends MapPropertyMapView<K, V> {
        private final MapPropertyInternal<K, V> property;

        ConventionKeepingMapView(MapProperty<K, V> property) {
            super(property);
            this.property = Cast.uncheckedNonnullCast(property);
        }

        boolean isViewOf(Object other) {
            return property == other;
        }

        @Override
        protected void putEntry(K key, V value) {
            property.insert(key, value);
        }

        @Override
        protected void putEntries(Map<? extends K, ? extends V> entries) {
            property.insertAll(entries);
        }
    }

    private static class ConventionKeepingListView<T> extends ListPropertyListView<T> {
        private final CollectionPropertyInternal<T, List<T>> property;

        ConventionKeepingListView(ListProperty<T> property) {
            super(property);
            this.property = Cast.uncheckedNonnullCast(property);
        }

        boolean isViewOf(Object other) {
            return property == other;
        }

        @Override
        protected void addElement(T element) {
            property.append(element);
        }
    }
}
