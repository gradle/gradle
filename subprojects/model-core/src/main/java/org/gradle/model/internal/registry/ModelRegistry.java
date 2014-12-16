/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.registry;

import org.gradle.api.Nullable;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelRegistrar;
import org.gradle.model.internal.core.ModelSearchResult;
import org.gradle.model.internal.type.ModelType;

public interface ModelRegistry extends ModelRegistrar {

    public <T> T get(ModelPath path, ModelType<T> type);

    @Nullable
    <T> T find(ModelPath path, ModelType<T> type);

    ModelSearchResult search(ModelPath path);

    public ModelNode node(ModelPath path);

    void remove(ModelPath path);

    void validate() throws UnboundModelRulesException;
}
