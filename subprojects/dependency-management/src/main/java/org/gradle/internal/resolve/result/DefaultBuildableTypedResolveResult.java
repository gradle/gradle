/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resolve.result;

import org.gradle.api.Nullable;

public class DefaultBuildableTypedResolveResult<T, E extends Throwable> extends DefaultResourceAwareResolveResult implements BuildableTypedResolveResult<T, E> {
    private T result;
    private E failure;

    @Override
    public void failed(E failure) {
        this.result = null;
        this.failure = failure;
    }

    @Override
    public void resolved(T result) {
        this.result = result;
        this.failure = null;
    }

    @Override
    public T getResult() throws E {
        assertHasResult();
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    @Nullable
    @Override
    public E getFailure() {
        assertHasResult();
        return failure;
    }

    public boolean isSuccessful() {
        return result != null;
    }

    @Override
    public boolean hasResult() {
        return failure != null || result != null;
    }

    protected void assertHasResult() {
        if (!hasResult()) {
            throw new IllegalStateException("No result has been specified.");
        }
    }
}
