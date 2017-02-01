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
package org.gradle.api.internal.artifacts;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.Map;
import java.util.WeakHashMap;

public class DefaultImmutableModuleIdentifierFactory implements ImmutableModuleIdentifierFactory {
    // In order to avoid memory leaks and keep the cache usage cheap, we use a global weak hash map, which key is a group id
    // If the group id is not referenced anymore, it will release the whole cache for this group id.
    // For this to work, the ModuleIdentifier *MUST* use a different string as the group
    private final Map<String, Map<String, ModuleIdentifier>> groupIdToModules = new WeakHashMap<String, Map<String, ModuleIdentifier>>();
    private final Map<ModuleIdentifier, Map<String, ModuleVersionIdentifier>> idToVersions = new WeakHashMap<ModuleIdentifier, Map<String, ModuleVersionIdentifier>>();

    private final Object lock = new Object();

    @Override
    public ModuleIdentifier module(String group, String name) {
        Map<String, ModuleIdentifier> byName = groupIdToModules.get(group);
        if (byName == null) {
            synchronized (lock) {
                byName = groupIdToModules.get(group);
            }
            if (byName == null) {
                byName = Maps.newConcurrentMap();
                groupIdToModules.put(group, byName);
            }
        }
        ModuleIdentifier moduleIdentifier = byName.get(name);
        if (moduleIdentifier == null) {
            String groupCopy = new String(group); // This not *NOT* an error
            moduleIdentifier = DefaultModuleIdentifier.newId(groupCopy, name);
            byName.put(name, moduleIdentifier);
        }
        return moduleIdentifier;
    }

    @Override
    public ModuleVersionIdentifier moduleWithVersion(String group, String name, String version) {
        ModuleIdentifier mi = module(group, name);
        Map<String, ModuleVersionIdentifier> byVersion = idToVersions.get(mi);
        if (byVersion == null) {
            synchronized (lock) {
                byVersion = idToVersions.get(mi);
                if (byVersion == null) {
                    byVersion = Maps.newConcurrentMap();
                    idToVersions.put(mi, byVersion);
                }
            }
        }
        ModuleVersionIdentifier identifier = byVersion.get(version);
        if (identifier == null) {
            identifier = new DefaultModuleVersionIdentifier(mi, version);
            byVersion.put(version, identifier);
        }
        return identifier;
    }

    @Override
    public ModuleVersionIdentifier moduleWithVersion(Module module) {
        return moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
    }
}
