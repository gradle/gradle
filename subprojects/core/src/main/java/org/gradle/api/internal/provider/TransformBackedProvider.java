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
import org.gradle.api.provider.Provider;

class TransformBackedProvider<OUT, IN> extends AbstractMappingProvider<OUT, IN> {
    private final Transformer<? extends OUT, ? super IN> transformer;

    public TransformBackedProvider(Transformer<? extends OUT, ? super IN> transformer, Provider<? extends IN> provider) {
        super(null, provider);
        this.transformer = transformer;
    }

    @Override
    protected OUT map(IN v) {
        OUT result = transformer.transform(v);
        if (result == null) {
            throw new IllegalStateException(Providers.NULL_TRANSFORMER_RESULT);
        }
        return result;
    }
}
