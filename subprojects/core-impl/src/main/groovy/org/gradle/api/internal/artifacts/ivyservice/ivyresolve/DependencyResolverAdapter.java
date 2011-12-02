/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;

/**
 * A wrapper around an Ivy {@link DependencyResolver}.
 */
public class DependencyResolverAdapter extends DelegatingDependencyResolver implements ModuleVersionRepository {
    private final String id;
    private ResolverSettings settings;

    public DependencyResolverAdapter(String id, DependencyResolver resolver) {
        super(resolver);
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public void setSettings(ResolverSettings settings) {
        // Ignore
        this.settings = settings;
    }

    public boolean isChanging(ResolvedModuleRevision revision, ResolveData resolveData) {
        ChangingModuleDetector detector = new ChangingModuleDetector(settings);
        return detector.isChangingModule(getResolver(), revision, resolveData);
    }
}
