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
package org.gradle.composite;

import org.gradle.tooling.ConfigurableLauncher;
import org.gradle.tooling.GradleConnectionException;

import java.util.Set;

public interface CompositeModelBuilder<T> extends ConfigurableLauncher<CompositeModelBuilder<T>> {
    CompositeModelBuilder<T> forTasks(String... tasks);
    CompositeModelBuilder<T> forTasks(Iterable<String> tasks);

    Set<ModelResult<T>> get() throws GradleConnectionException, IllegalStateException;
    void get(CompositeResultHandler<? super T> handler) throws IllegalStateException;
}
