/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class TransformBackedProvider<OUT, IN> extends AbstractMinimalProvider<OUT> {
    private final Transformer<? extends OUT, ? super IN> transformer;
    private final ProviderInternal<? extends IN> provider;

    public TransformBackedProvider(Transformer<? extends OUT, ? super IN> transformer, ProviderInternal<? extends IN> provider) {
        this.transformer = transformer;
        this.provider = provider;
    }

    @Nullable
    @Override
    public Class<OUT> getType() {
        // Could do a better job of inferring this
        return null;
    }

    public Transformer<? extends OUT, ? super IN> getTransformer() {
        return transformer;
    }

    @Override
    public ValueProducer getProducer() {
        return provider.getProducer();
    }

    @Override
    public ExecutionTimeValue<? extends OUT> calculateExecutionTimeValue() {
        ExecutionTimeValue<? extends IN> value = provider.calculateExecutionTimeValue();
        if (value.hasChangingContent()) {
            // Need the value contents in order to transform it to produce the value of this provider, so if the value or its contents are built by tasks, the value of this provider is also built by tasks
            return ExecutionTimeValue.changingValue(new TransformBackedProvider<OUT, IN>(transformer, value.toProvider()));
        } else {
            return ExecutionTimeValue.value(mapValue(value.toValue()));
        }
    }

    @Override
    protected Value<? extends OUT> calculateOwnValue(ValueConsumer consumer) {
        beforeRead();
        Value<? extends IN> value = provider.calculateValue(consumer);
        return mapValue(value);
    }

    @NotNull
    private Value<? extends OUT> mapValue(Value<? extends IN> value) {
        if (value.isMissing()) {
            return value.asType();
        }
        OUT result = transformer.transform(value.get());
        if (result == null) {
            return Value.missing();
        }
        return Value.of(result);
    }

    private void beforeRead() {
        provider.getProducer().visitContentProducerTasks(producer -> {
            if (!producer.getState().getExecuted()) {
                DeprecationLogger.deprecateAction(String.format("Querying the mapped value of %s before %s has completed", provider, producer))
                    .willBecomeAnErrorInGradle7()
                    .withUpgradeGuideSection(6, "querying_a_mapped_output_property_of_a_task_before_the_task_has_completed")
                    .nagUser();
            }
        });
    }

    @Override
    public String toString() {
        return "map(" + provider + ")";
    }
}
