/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.instance;

import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.type.ModelType;

/**
 * A marker interface that is implemented by views of managed elements.
 */
public interface ManagedInstance {
    /**
     * Returns the node that this managed instance is backed by.
     */
    MutableModelNode getBackingNode();

    /**
     * Returns the original model type of the instance. This can be used to look up the schema for the type.
     */
    ModelType<?> getManagedType();
}
