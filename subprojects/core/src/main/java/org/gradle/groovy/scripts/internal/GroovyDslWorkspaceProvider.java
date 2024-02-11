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

package org.gradle.groovy.scripts.internal;

import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.execution.workspace.impl.CacheBasedImmutableWorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.Closeable;
import java.io.IOException;

@ServiceScope(Scopes.UserHome.class)
public class GroovyDslWorkspaceProvider implements Closeable {

    private final CacheBasedImmutableWorkspaceProvider groovyDslWorkspace;

    public GroovyDslWorkspaceProvider(
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        FileAccessTimeJournal fileAccessTimeJournal,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        this.groovyDslWorkspace = CacheBasedImmutableWorkspaceProvider.createWorkspaceProvider(
            cacheBuilderFactory
                .createCacheBuilder("groovy-dsl")
                .withDisplayName("groovy-dsl"),
            fileAccessTimeJournal,
            cacheConfigurations
        );
    }

    public ImmutableWorkspaceProvider getWorkspace() {
        return groovyDslWorkspace;
    }

    @Override
    public void close() throws IOException {
        groovyDslWorkspace.close();
    }
}
