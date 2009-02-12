/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.artifacts.maven;

/**
 * An immutable mapping to map a dependency configuration to a Maven scope. This class has
 * implemented equality and hashcode based on its values not on object identity.
 *
 * @see org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
 * @author Hans Dockter
 */
public class Conf2ScopeMapping {
    private int priority;
    private String conf;
    private String scope;

    /**
     * @param priority The priority of this mapping
     * @param conf The configuration name
     * @param scope The Maven scope name
     */
    public Conf2ScopeMapping(int priority, String conf, String scope) {
        this.priority = priority;
        this.conf = conf;
        this.scope = scope;
    }

    /**
     * Returns the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Returns the dependency configuration name
     */
    public String getConf() {
        return conf;
    }

    /**
     * Returns the Maven scope name.
     */
    public String getScope() {
        return scope;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Conf2ScopeMapping that = (Conf2ScopeMapping) o;

        if (priority != that.priority) return false;
        if (conf != null ? !conf.equals(that.conf) : that.conf != null) return false;
        if (scope != null ? !scope.equals(that.scope) : that.scope != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = priority;
        result = 31 * result + (conf != null ? conf.hashCode() : 0);
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        return result;
    }
}
