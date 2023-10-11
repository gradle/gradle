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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.Cast;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VcsMappings;

import javax.inject.Inject;

public class DefaultVcsMappings implements VcsMappings {
    private final VcsMappingsStore vcsMappings;
    private final Gradle gradle;
    private final NotationParser<Object, ModuleIdentifier> notationParser;
    private final Object lock = new Object();

    @Inject
    public DefaultVcsMappings(VcsMappingsStore vcsMappings, Gradle gradle, NotationParser<Object, ModuleIdentifier> notationParser) {
        this.vcsMappings = vcsMappings;
        this.gradle = gradle;
        this.notationParser = notationParser;
    }

    @Override
    public VcsMappings all(Action<? super VcsMapping> rule) {
        vcsMappings.addRule(new DslAccessRule(rule, lock), gradle);
        return this;
    }

    @Override
    public VcsMappings withModule(String module, Action<? super VcsMapping> rule) {
        vcsMappings.addRule(new ModuleFilteredRule(notationParser.parseNotation(module), new DslAccessRule(rule, lock)), gradle);
        return this;
    }

    // Ensure that at most one action that may have access to the mutable state of the build runs at a given time
    // TODO - move this to a better home and reuse
    private static class DslAccessRule implements Action<VcsMapping> {
        private final Object lock;
        private final Action<? super VcsMapping> delegate;

        DslAccessRule(Action<? super VcsMapping> delegate, Object lock) {
            this.lock = lock;
            this.delegate = delegate;
        }

        @Override
        public void execute(VcsMapping vcsMapping) {
            synchronized (lock) {
                delegate.execute(vcsMapping);
            }
        }
    }

    private static class ModuleFilteredRule implements Action<VcsMapping> {
        private final ModuleIdentifier moduleIdentifier;
        private final Action<? super VcsMapping> delegate;

        private ModuleFilteredRule(ModuleIdentifier moduleIdentifier, Action<? super VcsMapping> delegate) {
            this.moduleIdentifier = moduleIdentifier;
            this.delegate = delegate;
        }

        @Override
        public void execute(VcsMapping mapping) {
            if (mapping.getRequested() instanceof ModuleComponentSelector) {
                ModuleComponentSelector moduleComponentSelector = Cast.uncheckedCast(mapping.getRequested());
                if (moduleIdentifier.equals(moduleComponentSelector.getModuleIdentifier())) {
                    delegate.execute(mapping);
                }
            }
        }
    }
}
