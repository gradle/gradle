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
package org.gradle.api.artifacts.maven;

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.maven.settings.Settings;

/**
 * @author Hans Dockter
 */
public interface MavenResolver extends DependencyResolver, PomFilterContainer {
    /**
     * Returns a maven settings object. This can be used for example to figure out where the local repository is located.
     * This property is filled after publishing. Before this property is null.
     */
    Settings getSettings();
}
