/*
 * Copyright 2020 the original author or authors.
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

import javax.annotation.Nullable;

class OrElseProvider<T> extends AbstractMinimalProvider<T> {
    private final ProviderInternal<T> left;
    private final ProviderInternal<? extends T> right;

    public OrElseProvider(ProviderInternal<T> left, ProviderInternal<? extends T> right) {
        this.left = left;
        this.right = right;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return left.getType();
    }

    @Override
    public ValueProducer getProducer() {
        if (left.isPresent()) {
            return left.getProducer();
        } else {
            return right.getProducer();
        }
    }

    @Override
    public boolean isPresent() {
        return left.isPresent() || right.isPresent();
    }

    @Override
    protected Value<? extends T> calculateOwnValue() {
        Value<? extends T> leftValue = left.calculateValue();
        if (!leftValue.isMissing()) {
            return leftValue;
        }
        Value<? extends T> rightValue = right.calculateValue();
        if (!rightValue.isMissing()) {
            return rightValue;
        }
        return leftValue.addPathsFrom(rightValue);
    }
}
