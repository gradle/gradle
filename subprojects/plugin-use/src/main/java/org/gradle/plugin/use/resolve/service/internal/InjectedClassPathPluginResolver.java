/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.api.Transformer;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.internal.Factories;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginRequest;
import org.gradle.plugin.use.resolve.internal.InjectedClassPathPluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.net.URI;
import java.util.List;

public class InjectedClassPathPluginResolver implements PluginResolver {
    private final ClassLoaderScope parentScope;
    private final PluginInspector pluginInspector;
    private final List<URI> classpath;

    public InjectedClassPathPluginResolver(ClassLoaderScope parentScope, PluginInspector pluginInspector, List<URI> classpath) {
        this.parentScope = parentScope;
        this.pluginInspector = pluginInspector;
        this.classpath = classpath;
    }

    public void resolve(PluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        if(!classpath.isEmpty()) {
            List<File> classpathFiles = CollectionUtils.collect(classpath, new Transformer<File, URI>() {
                public File transform(URI uri) {
                    return new File(uri);
                }
            });

            ClassPath classPath = new DefaultClassPath(classpathFiles);
            PluginResolution resolution = new InjectedClassPathPluginResolution(pluginRequest.getId(), parentScope, Factories.constant(classPath), pluginInspector);
            result.found(getDescription(), resolution);
        }
    }

    public String getDescription() {
        return "Injected classpath";
    }
}
