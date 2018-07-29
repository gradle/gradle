/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VersionControlRepository;
import org.gradle.vcs.git.GitVersionControlSpec;

import javax.inject.Inject;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

class DefaultVersionControlRepository implements VersionControlRepository, Action<VcsMapping> {
    private final URI uri;
    private Set<String> modules = new HashSet<String>();

    @Inject
    public DefaultVersionControlRepository(URI uri) {
        this.uri = uri;
    }

    @Override
    public void producesModule(String name) {
        modules.add(name);
    }

    @Override
    public void execute(VcsMapping vcsMapping) {
        if (modules.isEmpty() || !(vcsMapping.getRequested() instanceof ModuleComponentSelector)) {
            return;
        }
        ModuleComponentSelector moduleSelector = (ModuleComponentSelector) vcsMapping.getRequested();
        // TODO - use a notation parser instead
        String expected = moduleSelector.getGroup() + ":" + moduleSelector.getModule();
        if (modules.contains(expected)) {
            vcsMapping.from(GitVersionControlSpec.class, new Action<GitVersionControlSpec>() {
                @Override
                public void execute(GitVersionControlSpec spec) {
                    spec.setUrl(uri);
                }
            });
        }
    }

    public Action<VcsMapping> asMappingAction() {
        return this;
    }
}
