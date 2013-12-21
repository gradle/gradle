/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.idea;

import org.gradle.tooling.model.idea.IdeaDependencyScope;

import java.io.Serializable;

public class DefaultIdeaDependencyScope implements IdeaDependencyScope, Serializable {

    String scope;

    public DefaultIdeaDependencyScope(String scope) {
        this.scope = scope;
    }

    public String getScope() {
        return scope;
    }

    @Override
    public String toString() {
        return "IdeaDependencyScope{"
                + "scope='" + scope + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultIdeaDependencyScope)) {
            return false;
        }

        DefaultIdeaDependencyScope that = (DefaultIdeaDependencyScope) o;

        if (scope != null ? !scope.equals(that.scope) : that.scope != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return scope != null ? scope.hashCode() : 0;
    }
}
