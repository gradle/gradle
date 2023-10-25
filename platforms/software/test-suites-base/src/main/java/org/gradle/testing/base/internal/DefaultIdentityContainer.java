/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.base.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.testing.base.Identity;
import org.gradle.testing.base.IdentityContainer;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class DefaultIdentityContainer<V extends IdentityContainer.Value> implements IdentityContainer<V> {
    // TODO: move `Specs` out of base-services-groovy (???) and use here
    private static final Spec<Identity> ALWAYS_SATISFIED = element -> true;

    private final Provider<Set<V>> values;
    private final Map<Spec<? super Identity>, ImmutableActionSet<V>> specActions = new HashMap<>();
    private boolean used;

    protected DefaultIdentityContainer(Function<Identity, V> valueFactory) {
        this.values = getProviderFactory().<Set<V>, ValuesSourceParameters<V>>of(
            Cast.uncheckedCast(ValuesSource.class),
            spec -> spec.parameters(p -> {
                p.getIdentities().set(getIdentities());
                p.getValueFactory().set(valueFactory);
                Provider<Map<Spec<? super Identity>, ImmutableActionSet<V>>> specActionsProvider = getProviderFactory()
                    .provider(() -> specActions);
                Providers.internal(specActionsProvider).withSideEffect(__ -> used = true);
                p.getSpecActions().set(
                    specActionsProvider
                );
            })
        );
    }

    public static abstract class ValuesSource<V extends IdentityContainer.Value> implements ValueSource<Set<V>, ValuesSourceParameters<V>> {
        @Nullable
        @Override
        public Set<V> obtain() {
            ValuesSourceParameters<V> p = getParameters();
            Set<V> values = p.getIdentities().get().stream().map(p.getValueFactory().get()).collect(Collectors.toSet());
            for (V value : values) {
                for (Map.Entry<Spec<? super Identity>, ImmutableActionSet<V>> entry : p.getSpecActions().get().entrySet()) {
                    if (entry.getKey().isSatisfiedBy(value.getIdentity())) {
                        entry.getValue().execute(value);
                    }
                }
            }
            return values;
        }
    }

    public interface ValuesSourceParameters<V> extends ValueSourceParameters {
        Property<Function<Identity, V>> getValueFactory();

        SetProperty<Identity> getIdentities();

        Property<Map<Spec<? super Identity>, ImmutableActionSet<V>>> getSpecActions();
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Override
    public Provider<Set<V>> getValues() {
        return values;
    }

    @Override
    public void all(Action<? super V> action) {
        matching(ALWAYS_SATISFIED, action);
    }

    @Override
    public void matching(Spec<? super Identity> spec, Action<? super V> action) {
        if (used) {
            throw new IllegalStateException("Cannot add configuration after values have been realized");
        }
        ImmutableActionSet<V> actions = specActions.get(spec);
        if (actions == null) {
            actions = ImmutableActionSet.empty();
        }
        actions = actions.add(action);
        specActions.put(spec, actions);
    }
}
