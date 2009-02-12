/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.artifacts.IvyObjectBuilder;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public interface SettingsConverter extends IvyObjectBuilder<IvySettings> {
    String CHAIN_RESOLVER_NAME = "chain";
    String CLIENT_MODULE_CHAIN_NAME = "clientModuleChain";
    String CLIENT_MODULE_NAME = "clientModule";

    IvySettings convert(List<DependencyResolver> classpathResolvers, List<DependencyResolver> otherResolvers, File gradleUserHome, RepositoryResolver buildResolver,
                        Map clientModuleRegistry);
}
