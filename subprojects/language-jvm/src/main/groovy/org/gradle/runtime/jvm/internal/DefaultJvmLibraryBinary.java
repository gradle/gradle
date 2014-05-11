/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.runtime.jvm.internal;

import org.gradle.runtime.base.ProjectBinary;
import org.gradle.runtime.base.internal.AbstractBuildableModelElement;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.jvm.JvmLibrary;

public class DefaultJvmLibraryBinary extends AbstractBuildableModelElement implements ProjectBinary, JvmLibraryBinaryInternal {
    private final JvmLibrary library;
    private final BinaryNamingScheme namingScheme;

    public DefaultJvmLibraryBinary(JvmLibrary library, BinaryNamingScheme namingScheme) {
        this.library = library;
        this.namingScheme = namingScheme;
    }

    public boolean isBuildable() {
        return true;
    }

    public String getDisplayName() {
        return namingScheme.getDescription();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getName() {
        return namingScheme.getLifecycleTaskName();
    }

    public JvmLibrary getLibrary() {
        return library;
    }

    public BinaryNamingScheme getNamingScheme() {
        return namingScheme;
    }
}
