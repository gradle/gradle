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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VersionControlRepository;
import org.gradle.vcs.git.GitVersionControlSpec;

import javax.inject.Inject;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

class DefaultVersionControlRepository implements VersionControlRepository, Action<VcsMapping> {
    private final URI uri;
    private final NotationParser<String, ModuleIdentifier> notationParser;
    private Set<ModuleIdentifier> modules = new HashSet<ModuleIdentifier>();

    @Inject
    public DefaultVersionControlRepository(URI uri, NotationParser<String, ModuleIdentifier> notationParser) {
        this.uri = uri;
        this.notationParser = notationParser;
    }

    @Override
    public void producesModule(String module) {
        modules.add(notationParser.parseNotation(module));
    }

    @Override
    public void execute(VcsMapping vcsMapping) {
        if (modules.isEmpty() || !(vcsMapping.getRequested() instanceof ModuleComponentSelector)) {
            return;
        }
        ModuleComponentSelector moduleSelector = (ModuleComponentSelector) vcsMapping.getRequested();
        if (modules.contains(moduleSelector.getModuleIdentifier())) {
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
