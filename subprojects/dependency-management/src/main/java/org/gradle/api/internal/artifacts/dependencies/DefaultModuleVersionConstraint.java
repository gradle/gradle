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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.internal.artifacts.ModuleVersionConstraintInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;

import static com.google.common.base.Strings.nullToEmpty;

public class DefaultModuleVersionConstraint implements ModuleVersionConstraintInternal {
    private final String version;
    private final boolean strict;

    public DefaultModuleVersionConstraint(String version, boolean strict) {
        this.version = nullToEmpty(version);
        this.strict = strict;
    }

    public DefaultModuleVersionConstraint(String version) {
        this(version, false);
    }

    @Override
    public VersionSelector getPreferredSelector(VersionSelectorScheme versionSelectorScheme) {
        return versionSelectorScheme.parseSelector(version);
    }

    @Override
    public VersionSelector getRejectionSelector(VersionSelectorScheme versionSelectorScheme) {
        return strict ? new NotVersionSelector(getPreferredSelector(versionSelectorScheme)) : null;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleVersionConstraint that = (DefaultModuleVersionConstraint) o;

        if (strict != that.strict) {
            return false;
        }
        return version != null ? version.equals(that.version) : that.version == null;
    }

    @Override
    public int hashCode() {
        int result = version != null ? version.hashCode() : 0;
        result = 31 * result + (strict ? 1 : 0);
        return result;
    }
}
