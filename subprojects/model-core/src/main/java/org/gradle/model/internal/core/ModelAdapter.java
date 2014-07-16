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

package org.gradle.model.internal.core;

import org.gradle.api.Nullable;

public interface ModelAdapter {

    @Nullable // if the model can't be viewed as this type
    <T> ModelView<? extends T> asWritable(ModelType<T> type);

    @Nullable // if the model can't be viewed as this type
    <T> ModelView<? extends T> asReadOnly(ModelType<T> type);

    // TODO some kind of description of the model item?

}
