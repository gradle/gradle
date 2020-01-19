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

import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.ArrayList;
import java.util.List;

public class TransformBackedProvider<OUT, IN> extends AbstractMappingProvider<OUT, IN> {
    private final Transformer<? extends OUT, ? super IN> transformer;

    public TransformBackedProvider(Transformer<? extends OUT, ? super IN> transformer, ProviderInternal<? extends IN> provider) {
        super(null, provider);
        this.transformer = transformer;
    }

    public Transformer<? extends OUT, ? super IN> getTransformer() {
        return transformer;
    }

    @Override
    public boolean isValueProducedByTask() {
        // Need the content in order to transform it to produce the value of this provider, so if the content is built by tasks, the value is also built by tasks
        return super.isValueProducedByTask() || !getProducerTasks().isEmpty();
    }

    @Override
    protected void beforeRead() {
        for (Task producer : getProducerTasks()) {
            if (!producer.getState().getExecuted()) {
                DeprecationLogger.deprecateAction(String.format("Querying the mapped value of %s before %s has completed", getProvider(), producer)).undocumented().nagUser();
                break; // Only report one producer
            }
        }
    }

    @Override
    protected OUT mapValue(IN v) {
        OUT result = transformer.transform(v);
        if (result == null) {
            throw new IllegalStateException(Providers.NULL_TRANSFORMER_RESULT);
        }
        return result;
    }

    private List<Task> getProducerTasks() {
        List<Task> producers = new ArrayList<>();
        getProvider().visitProducerTasks(producers::add);
        return producers;
    }
}
