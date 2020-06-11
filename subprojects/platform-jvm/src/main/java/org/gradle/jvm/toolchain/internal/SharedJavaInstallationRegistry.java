/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapMaker;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class SharedJavaInstallationRegistry {

    private final ConcurrentMap<String, File> installations = new MapMaker().makeMap();
    private final Supplier<ImmutableMap<String, File>> finalizedInstallations = Suppliers.memoize(() -> ImmutableMap.copyOf(installations));
    private boolean finalized;

    public void add(String name, File file) {
        Preconditions.checkArgument(!finalized, "Installation must not be mutated after being finalized");
        Preconditions.checkArgument(!installations.containsKey(name), "Installation with name '%s' already exists", name);
        installations.put(name, file);
    }

    public void finalizeValue() {
        finalized = true;
    }

    public Map<String, File> listInstallations() {
        finalizeValue();
        return finalizedInstallations.get();
    }

}
