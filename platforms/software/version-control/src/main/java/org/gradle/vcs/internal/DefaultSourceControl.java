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

package org.gradle.vcs.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.vcs.SourceControl;
import org.gradle.vcs.VcsMappings;
import org.gradle.vcs.VersionControlRepository;
import org.gradle.vcs.git.GitVersionControlSpec;

import javax.inject.Inject;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class DefaultSourceControl implements SourceControl {
    private final FileResolver fileResolver;
    private final VcsMappings vcsMappings;
    private final VersionControlSpecFactory specFactory;
    private final Map<URI, DefaultVersionControlRepository> repos = new HashMap<URI, DefaultVersionControlRepository>();

    @Inject
    public DefaultSourceControl(FileResolver fileResolver, VcsMappings vcsMappings, VersionControlSpecFactory specFactory) {
        this.fileResolver = fileResolver;
        this.vcsMappings = vcsMappings;
        this.specFactory = specFactory;
    }

    @Override
    public void vcsMappings(Action<? super VcsMappings> configuration) {
        configuration.execute(vcsMappings);
    }

    @Override
    public VcsMappings getVcsMappings() {
        return vcsMappings;
    }

    @Override
    public VersionControlRepository gitRepository(URI url) {
        DefaultVersionControlRepository repo = repos.get(url);
        if (repo == null) {
            repo = specFactory.create(GitVersionControlSpec.class, url);
            repos.put(url, repo);
            vcsMappings.all(repo.asMappingAction());
        }
        return repo;
    }

    // Mix in to Groovy DSL
    public VersionControlRepository gitRepository(String uri) {
        return gitRepository(fileResolver.resolveUri(uri));
    }

    @Override
    public void gitRepository(URI url, Action<? super VersionControlRepository> configureAction) {
        configureAction.execute(gitRepository(url));
    }

    // Mix in to Groovy DSL
    public void gitRepository(String uri, Action<? super VersionControlRepository> configureAction) {
        gitRepository(fileResolver.resolveUri(uri), configureAction);
    }
}
