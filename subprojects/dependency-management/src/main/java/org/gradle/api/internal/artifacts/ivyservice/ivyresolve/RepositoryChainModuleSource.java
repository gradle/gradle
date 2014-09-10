/*
 * Copyright 2014 the original author or authors.
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

class RepositoryChainModuleSource implements ModuleSource {
    private final String repositoryId;
    private final ModuleSource delegate;

    public RepositoryChainModuleSource(String repositoryId, ModuleSource delegate) {
        this.repositoryId = repositoryId;
        this.delegate = delegate;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public ModuleSource getDelegate() {
        return delegate;
    }
}
