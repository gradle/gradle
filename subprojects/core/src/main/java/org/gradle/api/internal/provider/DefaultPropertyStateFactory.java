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

package org.gradle.api.internal.provider;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.provider.ConfigurablePropertyState;
import org.gradle.api.provider.PropertyStateFactory;

public class DefaultPropertyStateFactory implements PropertyStateFactory {

    private final TaskResolver taskResolver;

    public DefaultPropertyStateFactory() {
        this(null);
    }

    public DefaultPropertyStateFactory(TaskResolver taskResolver) {
        this.taskResolver = taskResolver;
    }

    @Override
    public <T> ConfigurablePropertyState<T> property(Class<T> clazz) {
        if (clazz == null) {
            throw new InvalidUserDataException("Class cannot be null");
        }

        return new DefaultConfigurablePropertyState<T>(taskResolver);
    }
}
