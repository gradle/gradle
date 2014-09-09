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

package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;

import java.io.File;

public class DefaultSettings extends BaseSettings {

    public DefaultSettings(ServiceRegistryFactory serviceRegistryFactory,
                    GradleInternal gradle, ClassLoaderScope classLoaderScope,
                    ClassLoaderScope rootClassLoaderScope, File settingsDir,
                    ScriptSource settingsScript, StartParameter startParameter) {
        super(serviceRegistryFactory, gradle, classLoaderScope, rootClassLoaderScope, settingsDir, settingsScript, startParameter);
    }

}
