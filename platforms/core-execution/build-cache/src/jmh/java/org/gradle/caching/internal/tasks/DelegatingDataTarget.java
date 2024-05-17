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

package org.gradle.caching.internal.tasks;

import java.io.IOException;
import java.io.OutputStream;

public abstract class DelegatingDataTarget implements DataTarget {
    private final DataTarget delegate;

    public DelegatingDataTarget(DataTarget delegate) {
        this.delegate = delegate;
    }

    @Override
    public OutputStream openOutput() throws IOException {
        return delegate.openOutput();
    }

    @Override
    public DataSource toSource() throws IOException {
        return delegate.toSource();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
