/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.hash.HashCode;

public class ModuleDescriptorHashModuleSource implements PersistentModuleSource {
    public static final int CODEC_ID = 2;
    private final HashCode descriptorHash;
    private final boolean changingModule;

    public ModuleDescriptorHashModuleSource(HashCode descriptorHash, boolean changingModule) {
        this.descriptorHash = descriptorHash;
        this.changingModule = changingModule;
    }

    @Override
    public String toString() {
        return "{descriptor: " + descriptorHash + ", changing: " + changingModule + "}";
    }

    public HashCode getDescriptorHash() {
        return descriptorHash;
    }

    public boolean isChangingModule() {
        return changingModule;
    }

    @Override
    public int getCodecId() {
        return CODEC_ID;
    }
}
