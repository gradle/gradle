/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r86;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

class CustomModelStreamingBuildAction<T> implements BuildAction<T> {
    private final Class<T> type;
    private final int value;

    public CustomModelStreamingBuildAction(Class<T> type, int value) {
        this.type = type;
        this.value = value;
    }

    @Override
    public T execute(BuildController controller) {
        controller.send(new CustomModel(value));
        return controller.getModel(type);
    }
}
