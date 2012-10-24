/*
 * Copyright 2012 the original author or authors.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CachePolicyLookupRegistry {
    Map<ModuleRevisionId, Set<String>> lookupEntries = new HashMap<ModuleRevisionId, Set<String>>();

    public Set<String> get(ModuleRevisionId moduleRevisionId) {
        if(lookupEntries.containsKey(moduleRevisionId)){
            return lookupEntries.get(moduleRevisionId);
        }else{
            return new HashSet<String>();
        }
    }

    public void add(ModuleRevisionId moduleRevisionId, String lookedUpRepositories) {
        final Set<String> repositoryIds = get(moduleRevisionId);
        repositoryIds.add(lookedUpRepositories);
        lookupEntries.put(moduleRevisionId, repositoryIds);
    }
}
