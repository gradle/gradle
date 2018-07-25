/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.initialization.definition.SelfResolvingPluginRequest;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;

public class SelfResolvingRequestPluginResolver implements PluginResolver {
    private static final Factory<ClassPath> EMPTY_CLASSPATH_FACTORY = Factories.constant(ClassPath.EMPTY);
    private final PluginInspector pluginInspector;

    public SelfResolvingRequestPluginResolver(PluginInspector pluginInspector) {
        this.pluginInspector = pluginInspector;
    }

    @Override
    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if (pluginRequest instanceof SelfResolvingPluginRequest) {
            ClassLoaderScope classLoaderScope = ((SelfResolvingPluginRequest) pluginRequest).getClassLoaderScope();
            PluginResolution pluginResolution = new ClassPathPluginResolution(pluginRequest.getId(), classLoaderScope, EMPTY_CLASSPATH_FACTORY, pluginInspector);
            result.found("injected from outer build", pluginResolution);
        }
    }
}
