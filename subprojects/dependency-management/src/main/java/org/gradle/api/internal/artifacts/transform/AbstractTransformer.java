/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.hash.HashCode;

public abstract class AbstractTransformer<T> implements Transformer {
    private final Class<? extends T> implementationClass;
    private final HashCode inputsHash;
    private final ImmutableAttributes fromAttributes;

    public AbstractTransformer(Class<? extends T> implementationClass, HashCode inputsHash, ImmutableAttributes fromAttributes) {
        this.implementationClass = implementationClass;
        this.inputsHash = inputsHash;
        this.fromAttributes = fromAttributes;
    }

    @Override
    public ImmutableAttributes getFromAttributes() {
        return fromAttributes;
    }

    @Override
    public HashCode getSecondaryInputHash() {
        return inputsHash;
    }

    @Override
    public Class<? extends T> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public String getDisplayName() {
        return implementationClass.getSimpleName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractTransformer<?> that = (AbstractTransformer<?>) o;

        return inputsHash.equals(that.inputsHash);
    }

    @Override
    public int hashCode() {
        return inputsHash.hashCode();
    }
}
