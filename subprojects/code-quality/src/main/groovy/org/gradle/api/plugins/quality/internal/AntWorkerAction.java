/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.antbuilder.DefaultIsolatedAntBuilder;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

public abstract class AntWorkerAction<T extends WorkParameters> implements WorkAction<T> {

    @Override
    public void execute() {
        ModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get());
        ClassLoaderFactory classLoaderFactory = new DefaultClassLoaderFactory();
        ClassPathRegistry classPathRegistry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry));
        IsolatedAntBuilder antBuilder = new DefaultIsolatedAntBuilder(classPathRegistry, classLoaderFactory, moduleRegistry);
        callAnt(antBuilder);
    }

    protected abstract void callAnt(IsolatedAntBuilder antBuilder);

}
