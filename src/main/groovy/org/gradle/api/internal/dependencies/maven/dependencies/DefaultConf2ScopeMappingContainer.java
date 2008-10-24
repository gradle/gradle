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
package org.gradle.api.internal.dependencies.maven.dependencies;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.dependencies.maven.Conf2ScopeMapping;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultConf2ScopeMappingContainer implements Conf2ScopeMappingContainer {
    private Map<String, Conf2ScopeMapping> mappings = new HashMap<String, Conf2ScopeMapping>();

    private boolean skipUnmappedConfs = true;
                  
    public DefaultConf2ScopeMappingContainer() {
    }

    public DefaultConf2ScopeMappingContainer(Map<String, Conf2ScopeMapping> mappings) {
        this.mappings.putAll(mappings);
    }

    public String getScope(String... configurations) {
        List<Conf2ScopeMapping> mappingWithHighestPriority = getMappingsWithHighestPriority(configurations);
        if (mappingWithHighestPriority.size() == 0) {
            return null;
        }
        if (hasDifferentScopes(mappingWithHighestPriority)) {
            throw new InvalidUserDataException("Mappings with same priority map to different scopes: " + mappingWithHighestPriority);
        }
        return mappingWithHighestPriority.get(0).getScope();
    }

    private boolean hasDifferentScopes(List<Conf2ScopeMapping> mappings) {
        Set<String> scopes = new HashSet<String>();
        for (Conf2ScopeMapping mapping : mappings) {
            scopes.add(mapping.getScope());
        }
        return scopes.size() > 1;
    }

    private List<Conf2ScopeMapping> getMappingsWithHighestPriority(String[] configurations) {
        List<Conf2ScopeMapping> sortedExistingMappings = getSortedExistingMappings(configurations);
        if (sortedExistingMappings.size() <= 1) {
            return sortedExistingMappings;
        }
        int highestPriority = sortedExistingMappings.get(sortedExistingMappings.size() - 1).getPriority();
        int i = sortedExistingMappings.size() - 2;
        for ( ; i >= 0; i--) {
            if (sortedExistingMappings.get(i).getPriority() < highestPriority) {
                break;
            }
        }
        return sortedExistingMappings.subList(i + 1, sortedExistingMappings.size());
    }

    private List<Conf2ScopeMapping> getSortedExistingMappings(String[] configurations) {
        List<Conf2ScopeMapping> existingMappings = new ArrayList<Conf2ScopeMapping>();
        for (String configuration : configurations) {
            if (mappings.get(configuration) != null) {
                existingMappings.add(mappings.get(configuration));
            }
        }
        Collections.sort(existingMappings, new Comparator<Conf2ScopeMapping>() {
            public int compare(Conf2ScopeMapping o1, Conf2ScopeMapping o2) {
                return new Integer(o1.getPriority()).compareTo(o2.getPriority());
            }
        });
        return existingMappings;
    }


    public Conf2ScopeMappingContainer addMapping(int priority, String configuration, String scope) {
        mappings.put(configuration, new Conf2ScopeMapping(priority, configuration, scope));
        return this;
    }


    public Conf2ScopeMapping getMapping(String configuration) {
        return mappings.get(configuration);
    }

    public Map<String, Conf2ScopeMapping> getMappings() {
        return mappings;
    }

    public boolean isSkipUnmappedConfs() {
        return skipUnmappedConfs;
    }

    public void setSkipUnmappedConfs(boolean skipUnmappedConfs) {
        this.skipUnmappedConfs = skipUnmappedConfs;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultConf2ScopeMappingContainer that = (DefaultConf2ScopeMappingContainer) o;

        if (!mappings.equals(that.mappings)) return false;

        return true;
    }

    public int hashCode() {
        return mappings.hashCode();
    }
}
