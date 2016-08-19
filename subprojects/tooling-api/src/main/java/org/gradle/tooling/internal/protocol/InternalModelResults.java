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

package org.gradle.tooling.internal.protocol;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

/**
 * The internal protocol for transferring {@link org.gradle.tooling.connection.ModelResults}
 */
public class InternalModelResults<T> implements Iterable<InternalModelResult<T>>, Serializable {
    private final List<InternalModelResult<T>> results;

    public InternalModelResults(List<InternalModelResult<T>> results) {
        this.results = results;
    }

    @Override
    public Iterator<InternalModelResult<T>> iterator() {
        return results.iterator();
    }
}
