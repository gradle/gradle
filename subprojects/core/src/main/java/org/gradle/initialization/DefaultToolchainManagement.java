/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.plugins.ExtensionContainerInternal;
import org.gradle.internal.FinalizableValue;
import org.gradle.internal.management.ToolchainManagementInternal;

public abstract class DefaultToolchainManagement implements ToolchainManagementInternal {

    @Override
    public void preventFromFurtherMutation() {
        ExtensionContainerInternal extensions = (ExtensionContainerInternal) getExtensions();
        extensions.getAsMap().values().stream()
                .filter(ext -> ext instanceof FinalizableValue)
                .map(ext -> (FinalizableValue) ext)
                .forEach(FinalizableValue::preventFromFurtherMutation);
    }

}
