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

package org.gradle.api.internal.changedetection.state.isolation;

/**
 * Isolatable objects can return an isolated instance of the given type T from which this object was created.
 * An <b>isolated</b> instance has the same internal state as the original object on which this isolatable was based,
 * but it is guaranteed not to retain any references to mutable state from the original instance.
 * <p>
 * The primary reason to need such an isolated instance of an object is to ensure that work can be done in parallel using the instance without
 * fear that it's internal state is changing while the work is being carried out.
 */
public interface Isolatable<T> {
    T isolate();
}
