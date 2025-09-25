/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.caching.configuration;

import org.gradle.api.Transformer;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Helpers for wiring a legacy boolean getter/setter into a minimal Property<Boolean>.
 */
final class PropertyBridges {
    private PropertyBridges() {}

    static Property<Boolean> booleanBridge(Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new BridgeBooleanProperty(getter, setter);
    }

    static Property<Boolean> booleanBacked(boolean defaultValue) {
        final AtomicBoolean box = new AtomicBoolean(defaultValue);
        return new BridgeBooleanProperty(box::get, box::set);
    }

    static final class BridgeBooleanProperty implements Property<Boolean> {
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;

        private final AtomicBoolean finalized = new AtomicBoolean(false);
        private final AtomicBoolean changesDisallowed = new AtomicBoolean(false);

        private final Provider<Boolean> view;

        BridgeBooleanProperty(Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.getter = Objects.requireNonNull(getter, "getter");
            this.setter = Objects.requireNonNull(setter, "setter");
            this.view  = ProviderViews.of(this::getOrNull);
        }


        @Override public Boolean get() { return getter.get(); }
        @Override public Boolean getOrNull() { return getter.get(); }
        @Override public Boolean getOrElse(Boolean defaultValue) { return view.getOrElse(defaultValue); }
        @Override public boolean isPresent() { return view.isPresent(); }

        @Override public <S> Provider<S> map(Transformer<? extends S, ? super Boolean> transformer) { return view.map(transformer); }
        @Override public Provider<Boolean> filter(Spec<? super Boolean> spec) { return view.filter(spec); }
        @Override public <S> Provider<S> flatMap(Transformer<? extends Provider<? extends S>, ? super Boolean> transformer) { return view.flatMap(transformer); }
        @Override public Provider<Boolean> orElse(Boolean value) { return view.orElse(value); }
        @Override public Provider<Boolean> orElse(Provider<? extends Boolean> provider) { return view.orElse(provider); }
        @Override public <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super Boolean, ? super U, ? extends R> combiner) { return view.zip(right, combiner); }


        @Override
        public void set(Boolean value) {
            checkCanChange();
            setter.accept(Boolean.TRUE.equals(value));
        }

        @Override
        public void set(Provider<? extends Boolean> provider) {
            Objects.requireNonNull(provider, "provider");
            checkCanChange();
            setter.accept(Boolean.TRUE.equals(provider.getOrNull()));
        }

        @Override public Property<Boolean> value(Boolean value) { set(value); return this; }
        @Override public Property<Boolean> value(Provider<? extends Boolean> provider) { set(provider); return this; }

        @Override
        public Property<Boolean> convention(Boolean value) {
            if (!isPresent() && value != null) set(value);
            return this;
        }

        @Override
        public Property<Boolean> convention(Provider<? extends Boolean> provider) {
            if (!isPresent()) set(provider);
            return this;
        }

        @Override public Property<Boolean> unset() { return this; }
        @Override public Property<Boolean> unsetConvention() { return this; }

        @Override public void disallowChanges() { changesDisallowed.set(true); }
        @Override public void finalizeValue() { finalized.set(true); }
        @Override public void finalizeValueOnRead() { finalized.set(true); }
        @Override public void disallowUnsafeRead() { }

        private void checkCanChange() {
            if (finalized.get()) throw new IllegalStateException("Cannot change value: property is finalized");
            if (changesDisallowed.get()) throw new IllegalStateException("Cannot change value: changes are disallowed");
        }
    }

    private static final class ProviderViews {
        private ProviderViews() {}

        static <T> Provider<T> of(Supplier<T> supplier) {
            Objects.requireNonNull(supplier, "supplier");
            return new SupplierBackedProvider<>(supplier);
        }

        private static final class SupplierBackedProvider<T> implements Provider<T> {
            private final Supplier<T> supplier;
            SupplierBackedProvider(Supplier<T> supplier) { this.supplier = supplier; }

            @Override public T get() { return supplier.get(); }
            @Override public T getOrNull() { return supplier.get(); }
            @Override public T getOrElse(T defaultValue) {
                T v = supplier.get();
                return (v != null) ? v : defaultValue;
            }
            @Override public boolean isPresent() { return supplier.get() != null; }

            @Override
            public <S> Provider<S> map(Transformer<? extends S, ? super T> transformer) {
                Objects.requireNonNull(transformer, "transformer");
                return of(() -> {
                    T v = supplier.get();
                    return (v == null) ? null : transformer.transform(v);
                });
            }

            @Override
            public Provider<T> filter(Spec<? super T> spec) {
                Objects.requireNonNull(spec, "spec");
                return of(() -> {
                    T v = supplier.get();
                    return (v != null && spec.isSatisfiedBy(v)) ? v : null;
                });
            }

            @Override
            public <S> Provider<S> flatMap(Transformer<? extends Provider<? extends S>, ? super T> transformer) {
                Objects.requireNonNull(transformer, "transformer");
                return of(() -> {
                    T v = supplier.get();
                    if (v == null) return null;
                    Provider<? extends S> p = transformer.transform(v);
                    return (p == null) ? null : p.getOrNull();
                });
            }

            @Override
            public Provider<T> orElse(T value) {
                return of(() -> {
                    T v = supplier.get();
                    return (v != null) ? v : value;
                });
            }

            @Override
            public Provider<T> orElse(Provider<? extends T> provider) {
                Objects.requireNonNull(provider, "provider");
                return of(() -> {
                    T v = supplier.get();
                    return (v != null) ? v : provider.getOrNull();
                });
            }

            @Override
            public <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super T, ? super U, ? extends R> combiner) {
                Objects.requireNonNull(right, "right");
                Objects.requireNonNull(combiner, "combiner");
                return of(() -> {
                    T l = supplier.get();
                    U r = right.getOrNull();
                    return (l != null && r != null) ? combiner.apply(l, r) : null;
                });
            }
        }
    }
}
