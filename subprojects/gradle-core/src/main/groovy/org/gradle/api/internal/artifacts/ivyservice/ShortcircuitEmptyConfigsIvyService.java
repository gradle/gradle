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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.artifacts.*;
import org.gradle.api.specs.Spec;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.io.File;

public class ShortcircuitEmptyConfigsIvyService implements IvyService {
    private final ResolvedConfiguration emptyConfig = new ResolvedConfiguration() {
        public boolean hasError() {
            return false;
        }

        public void rethrowFailure() throws ResolveException {
        }

        public Set<File> getFiles(Spec<Dependency> dependencySpec) {
            return Collections.emptySet();
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
            return Collections.emptySet();
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() {
            return Collections.emptySet();
        }
    };
    private final IvyService ivyService;

    public ShortcircuitEmptyConfigsIvyService(IvyService ivyService) {
        this.ivyService = ivyService;
    }

    public IvyService getIvyService() {
        return ivyService;
    }

    public void publish(Set<Configuration> configurationsToPublish, File descriptorDestination,
                        List<DependencyResolver> publishResolvers) {
        ivyService.publish(configurationsToPublish, descriptorDestination, publishResolvers);
    }

    public ResolvedConfiguration resolve(Configuration configuration) {
        if (configuration.getAllDependencies().isEmpty()) {
            return emptyConfig;
        }
        return ivyService.resolve(configuration);
    }
}
