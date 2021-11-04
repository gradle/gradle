/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.Transformer;

import javax.annotation.Nullable;

/**
 * <p>{@code Transformer} extension that explicitly allows {@code null} return values</p>
 *
 * @param <OUT> The type the value is transformed to.
 * @param <IN> The type of the value to be transformed.
 */
public abstract class NullableTransformer<OUT, IN> implements Transformer<OUT, IN> {

    @Nullable
    @Override
    public abstract OUT transform(IN in);

    public Transformer<OUT, IN> asTransformer() {
        return this;
    }
}
