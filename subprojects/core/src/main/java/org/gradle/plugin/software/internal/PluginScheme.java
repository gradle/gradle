/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal;

import org.gradle.api.Plugin;
import org.gradle.api.internal.tasks.properties.AbstractTypeScheme;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Scheme for instantiating and inspecting plugin classes.
 */
@ServiceScope(Scope.Build.class)
public class PluginScheme extends AbstractTypeScheme {

    public PluginScheme(InstantiationScheme instantiationScheme, InspectionScheme inspectionScheme) {
        super(instantiationScheme, inspectionScheme);
    }

    @Override
    public boolean appliesTo(Class<?> type) {
        return Plugin.class.isAssignableFrom(type);
    }
}
