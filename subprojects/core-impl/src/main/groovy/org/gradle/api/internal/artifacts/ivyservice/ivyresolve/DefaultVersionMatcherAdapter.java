/*
 * Copyright 2013 the original author or authors.
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

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.Comparator;

public class DefaultVersionMatcherAdapter implements VersionMatcher {
    private final org.apache.ivy.plugins.version.VersionMatcher delegate;

    public DefaultVersionMatcherAdapter(org.apache.ivy.plugins.version.VersionMatcher delegate) {
        this.delegate = delegate;
    }

    public boolean isDynamic(ModuleVersionIdentifier version) {
        return delegate.isDynamic(toRevision(version));
    }

    public boolean accept(ModuleVersionIdentifier requested, ModuleVersionIdentifier found) {
        return delegate.accept(toRevision(requested), toRevision(found));
    }

    public boolean needModuleMetadata(ModuleVersionIdentifier requested, ModuleVersionIdentifier found) {
        return delegate.needModuleDescriptor(toRevision(requested), toRevision(found));
    }

    public boolean accept(ModuleVersionIdentifier requested, ModuleVersionMetaData found) {
        return delegate.accept(toRevision(requested), found.getDescriptor());
    }

    public int compare(ModuleVersionIdentifier requested, ModuleVersionIdentifier found, Comparator comparator) {
        return delegate.compare(toRevision(requested), toRevision(found), comparator);
    }

    public String getName() {
        return delegate.getName();
    }

    private static ModuleRevisionId toRevision(ModuleVersionIdentifier version) {
        return ModuleRevisionId.newInstance(version.getGroup(), version.getName(), version.getVersion());
    }
}
