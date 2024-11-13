/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.isolated.models.legacy;

import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.internal.Describables;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.gradle.internal.isolated.models.IsolatedModelAccessKey;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class IsolatedModelProducingProvider<T> extends AbstractMinimalProvider<T> {

    private final IsolatedModelAccessKey<T> accessKey;
    private final CalculatedValue<T> calculatedValue;

    public IsolatedModelProducingProvider(
        IsolatedModelAccessKey<T> accessKey,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        @Nullable BuildIsolatedModel<T> buildIsolatedModel
    ) {
        this.accessKey = accessKey;
        calculatedValue = calculatedValueContainerFactory.create(Describables.of("Isolated model"), (Supplier<? extends T>) () -> {
            if (buildIsolatedModel == null) {
                return null;
            }
            return buildIsolatedModel.instantiate().get();
        });
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        calculatedValue.finalizeIfNotAlready();
        return true; // TODO: calculate presence
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            return produce();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private Value<? extends T> produce() {
        // TODO: handle errors
        calculatedValue.finalizeIfNotAlready();
        return Value.ofNullable(calculatedValue.get());
    }

    @Override
    public Class<T> getType() {
        return accessKey.getModelKey().getType();
    }
}
