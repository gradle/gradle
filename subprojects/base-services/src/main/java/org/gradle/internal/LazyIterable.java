/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal;

import java.util.Iterator;

public class LazyIterable<T> implements Iterable<T> {

    private final Factory<Iterable<T>> factory;

    public LazyIterable(Factory<Iterable<T>> factory) {
        this.factory = factory;
    }

    public Iterator<T> iterator() {
        return factory.create().iterator();
    }

}
