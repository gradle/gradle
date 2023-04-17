/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;

@ServiceScope(Scopes.Project.class)
public class DefaultConfigurationServices extends DefaultUniversalServices implements Project.ConfigurationServices {
    @Inject
    public DefaultConfigurationServices(
        ArchiveOperations archiveOperations,
        ObjectFactory objectFactory,
        ProjectLayout projectLayout,
        ProviderFactory providerFactory,
        FileSystemOperations fileSystemOperations
    ) {
        super(archiveOperations, objectFactory, projectLayout, providerFactory, fileSystemOperations);
    }
}
