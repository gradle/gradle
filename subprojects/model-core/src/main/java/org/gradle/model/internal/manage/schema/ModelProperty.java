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

package org.gradle.model.internal.manage.schema;

import org.gradle.model.internal.core.ModelType;

public class ModelProperty<T> {

    private final String name;
    private final ModelType<T> type;

    public ModelProperty(String name, ModelType<T> type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public ModelType<T> getType() {
        return type;
    }

}
