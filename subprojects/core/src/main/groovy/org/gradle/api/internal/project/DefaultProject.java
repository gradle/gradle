/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.NoConventionMapping;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;

import java.io.File;

@NoConventionMapping
public class DefaultProject extends AbstractProject {
    public DefaultProject(String name, ProjectInternal parent, File projectDir, ScriptSource buildScriptSource,
                          GradleInternal gradle, ServiceRegistryFactory serviceRegistryFactory, ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope) {
        super(name, parent, projectDir, buildScriptSource, gradle, serviceRegistryFactory, selfClassLoaderScope, baseClassLoaderScope);
    }
}
