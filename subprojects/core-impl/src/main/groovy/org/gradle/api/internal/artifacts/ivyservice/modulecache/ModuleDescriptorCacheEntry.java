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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;

import java.math.BigInteger;

class ModuleDescriptorCacheEntry {
    public boolean isChanging;
    public boolean isMetaDataOnly;
    public boolean isMissing;
    public long createTimestamp;
    public ModuleSource moduleSource;
    public BigInteger moduleDescriptorHash;

    ModuleDescriptorCacheEntry(boolean isChanging, boolean isMetaDataOnly, boolean isMissing, long createTimestamp, BigInteger moduleDescriptorHash, ModuleSource moduleSource) {
        this.isChanging = isChanging;
        this.isMetaDataOnly = isMetaDataOnly;
        this.isMissing = isMissing;
        this.createTimestamp = createTimestamp;
        this.moduleSource = moduleSource;
        this.moduleDescriptorHash = moduleDescriptorHash;
    }
}
