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

package org.gradle.nativeplatform.internal;

import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.nativeplatform.NativeLibrarySpec;
import org.gradle.nativeplatform.NativeLibraryRequirement;
import org.gradle.platform.base.ComponentSpecIdentifier;

public class DefaultNativeLibrarySpec extends AbstractTargetedNativeComponentSpec implements NativeLibrarySpec {
    public DefaultNativeLibrarySpec(ComponentSpecIdentifier id, FunctionalSourceSet sourceSet) {
        super(id, sourceSet);
    }

    public String getDisplayName() {
        return String.format("native library '%s'", getName());
    }

    public NativeLibraryRequirement getShared() {
        return new ProjectNativeLibraryRequirement(getProjectPath(), this.getName(), "shared");
    }

    public NativeLibraryRequirement getStatic() {
        return new ProjectNativeLibraryRequirement(getProjectPath(), this.getName(), "static");
    }

    public NativeLibraryRequirement getApi() {
        return new ProjectNativeLibraryRequirement(getProjectPath(), this.getName(), "api");
    }

}