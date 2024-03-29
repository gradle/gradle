/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.local;

import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.LocalBinaryResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.net.URI;

@ServiceScope(Scope.Build.class)
public interface FileResourceRepository extends ExternalResourceRepository {
    LocalBinaryResource localResource(File file);

    /**
     * Returns the given file as a resource.
     */
    LocallyAvailableExternalResource resource(File file);

    /**
     * Returns the given file as a resource, with the given origin details.
     */
    LocallyAvailableExternalResource resource(File file, URI originUri, ExternalResourceMetaData originMetadata);

    @Override
    LocallyAvailableExternalResource resource(ExternalResourceName resource);

    @Override
    LocallyAvailableExternalResource resource(ExternalResourceName resource, boolean revalidate);
}
