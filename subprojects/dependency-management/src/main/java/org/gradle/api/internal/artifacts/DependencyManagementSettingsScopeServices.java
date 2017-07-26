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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.repositories.resolver.DefaultExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceAccessor;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.authentication.Authentication;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.internal.resource.TextResourceLoader;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.transfer.DefaultUriTextResourceLoader;

import java.util.Collections;
import java.util.HashSet;

/**
 * The set of dependency management services that are created per build.
 */
class DependencyManagementSettingsScopeServices {

    ExternalResourceFileStore createExternalResourceFileStore(CacheScopeMapping cacheScopeMapping) {
        return new ExternalResourceFileStore(cacheScopeMapping.getBaseDirectory(null, "external-resources", VersionStrategy.SharedCache), new TmpDirTemporaryFileProvider());
    }

    TextResourceLoader createTextResourceLoader(ExternalResourceFileStore resourceFileStore, RepositoryTransportFactory repositoryTransportFactory) {
        HashSet<String> schemas = Sets.newHashSet("https", "http");
        RepositoryTransport transport = repositoryTransportFactory.createTransport(schemas, "http auth", Collections.<Authentication>emptyList());
        ExternalResourceAccessor externalResourceAccessor = new DefaultExternalResourceAccessor(resourceFileStore, transport.getResourceAccessor());
        return new DefaultUriTextResourceLoader(externalResourceAccessor, schemas);
    }
}
