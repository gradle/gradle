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

package org.gradle.api.internal.provider;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.provider.support.CompoundAssignmentSupport;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Cast;

import java.util.Arrays;
import java.util.Collections;

import static org.gradle.api.internal.lambdas.SerializableLambdas.bifunction;
import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

@SuppressWarnings("unused") // Groovy extension methods for ListProperty and SetProperty
public final class CollectionPropertyExtensions {
    private CollectionPropertyExtensions() {}

    public static <T> Provider<Iterable<T>> plus(HasMultipleValues<T> lhs, Iterable<T> rhs) {
        if (!CompoundAssignmentSupport.isEnabled()) {
            throw new UnsupportedOperationException("Cannot call plus() on " + typeName(lhs));
        }

        return plusImpl(Cast.uncheckedCast(lhs), rhs);
    }

    // Called for property += Iterable<T>
    private static <T> Provider<Iterable<T>> plusImpl(AbstractCollectionProperty<T, ?> lhs, Iterable<T> rhs) {
        return new CompoundAssignmentResultProvider<>(
            Providers.internal(lhs.map(transformer(items -> Iterables.concat(items, rhs)))),
            lhs,
            () -> lhs.addAll(rhs)
        );
    }

    // Called for:
    // property += Provider<Iterable<T>>
    // property += Provider<T>
    public static <T> Provider<Iterable<T>> plus(HasMultipleValues<T> lhs, Provider<?> rhs) {
        if (!CompoundAssignmentSupport.isEnabled()) {
            throw new UnsupportedOperationException("Cannot call plus() on " + typeName(lhs));
        }

        // Because of type erasure, we cannot have two overloads of this method for Provider<T> and Provider<Iterable<T>>.
        return plusImpl(Cast.uncheckedCast(lhs), Providers.internal(rhs));
    }

    // Called for property += T[]
    public static <T> Provider<Iterable<T>> plus(HasMultipleValues<T> lhs, T[] items) {
        if (!CompoundAssignmentSupport.isEnabled()) {
            throw new UnsupportedOperationException("Cannot call plus() on " + typeName(lhs));
        }

        return plusImpl(Cast.uncheckedCast(lhs), Arrays.asList(items));
    }

    private static <T> Provider<Iterable<T>> plusImpl(AbstractCollectionProperty<T, ?> lhs, ProviderInternal<?> rhs) {
        ProviderInternal<? extends Iterable<T>> safeProvider = toSafeProvider(rhs);
        return new CompoundAssignmentResultProvider<>(
            Providers.internal(lhs.zip(safeProvider, bifunction(Iterables::concat))),
            lhs,
            () -> lhs.addAll(safeProvider)
        );
    }

    // Called for property += T
    public static <T> Provider<Iterable<T>> plus(HasMultipleValues<T> lhs, T item) {
        if (!CompoundAssignmentSupport.isEnabled()) {
            throw new UnsupportedOperationException("Cannot call plus() on " + typeName(lhs));
        }

        return plusImpl(Cast.uncheckedCast(lhs), item);
    }

    private static <T> Provider<Iterable<T>> plusImpl(AbstractCollectionProperty<T, ?> lhs, Object item) {
        Preconditions.checkNotNull(item, "Cannot add a null element to a property of type %s.", lhs.getType().getSimpleName());
        Preconditions.checkArgument(
            lhs.getElementType().isInstance(item),
            "Cannot add an element of type %s to a property of type %s<%s>.",
            item.getClass().getSimpleName(),
            lhs.getType().getSimpleName(),
            lhs.getElementType().getSimpleName());
        T safeItem = lhs.getElementType().cast(item);

        return new CompoundAssignmentResultProvider<>(
            Providers.internal(lhs.map(transformer(thisItems -> Iterables.concat(thisItems, Collections.singleton(safeItem))))),
            lhs,
            () -> lhs.add(safeItem)
        );
    }

    private static <T> ProviderInternal<? extends Iterable<T>> toSafeProvider(Provider<?> provider) {
        ProviderInternal<?> internal = Providers.internal(provider);
        if (isProviderOfIterable(internal)) {
            // This is definitely a provider of iterable, it can be used as is.
            return Cast.uncheckedCast(internal);
        }
        // May still be provider of iterable, but we'll only know after it is computed.
        return internal.map(transformer(value -> {
            if (value instanceof Iterable) {
                return Cast.<Iterable<T>>uncheckedCast(value);
            }
            return Collections.singleton(Cast.uncheckedCast(value));
        }));
    }

    private static boolean isProviderOfIterable(ProviderInternal<?> internal) {
        Class<?> type = internal.getType();
        return type != null && Iterable.class.isAssignableFrom(type);
    }

    private static String typeName(HasMultipleValues<?> value) {
        if (value instanceof ListProperty) {
            return "ListProperty";
        } else if (value instanceof SetProperty) {
            return "SetProperty";
        }
        return value.getClass().getSimpleName();
    }
}
