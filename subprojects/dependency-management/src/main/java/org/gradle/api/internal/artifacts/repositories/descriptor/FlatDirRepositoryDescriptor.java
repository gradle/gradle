/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.descriptor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.io.File;
import java.util.Collection;

public final class FlatDirRepositoryDescriptor extends RepositoryDescriptor {

    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    private enum Property {
        DIRS,
    }

    public final ImmutableList<File> dirs;

    public FlatDirRepositoryDescriptor(String name, Collection<File> dirs) {
        super(name);
        this.dirs = ImmutableList.copyOf(dirs);
    }

    @Override
    public Type getType() {
        return Type.FLAT_DIR;
    }

    @Override
    protected void addProperties(ImmutableSortedMap.Builder<String, Object> builder) {
        builder.put(Property.DIRS.name(), dirs);
    }
}
