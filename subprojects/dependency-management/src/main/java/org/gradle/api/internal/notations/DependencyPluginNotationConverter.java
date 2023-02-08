/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.notations;

import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.api.internal.dependencies.PluginDependencyMarkerCoordinates;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.plugin.use.PluginDependency;

public class DependencyPluginNotationConverter implements NotationConverter<PluginDependency, MinimalExternalModuleDependency> {
    private final NotationConverter<MinimalExternalModuleDependency, MinimalExternalModuleDependency> minimalConverter;

    public DependencyPluginNotationConverter(NotationConverter<MinimalExternalModuleDependency, MinimalExternalModuleDependency> minimalConverter) {
        this.minimalConverter = minimalConverter;
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("PluginDependency").example("plugin('org.gradle.java:1.0')");
    }

    @Override
    public void convert(PluginDependency notation, NotationConvertResult<? super MinimalExternalModuleDependency> result) throws TypeConversionException {
        ModuleIdentifier module = DefaultModuleIdentifier.newId(notation.getPluginId(), PluginDependencyMarkerCoordinates.pluginName(notation.getPluginId()));
        MutableVersionConstraint version = new DefaultMutableVersionConstraint(notation.getVersion());
        MinimalExternalModuleDependency minimalDependency = new DefaultMinimalDependency(module, version);

        minimalConverter.convert(minimalDependency, result);
    }

}
