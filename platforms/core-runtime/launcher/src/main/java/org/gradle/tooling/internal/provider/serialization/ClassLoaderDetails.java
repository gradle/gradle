/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.serialization;

import org.gradle.internal.classloader.ClassLoaderSpec;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClassLoaderDetails implements Serializable {
    // TODO:ADAM - using a UUID means we create a ClassLoader hierarchy for each daemon process we talk to. Instead, use the spec to decide whether to reuse a ClassLoader
    final UUID uuid;
    final ClassLoaderSpec spec;
    final List<ClassLoaderDetails> parents = new ArrayList<ClassLoaderDetails>();

    public ClassLoaderDetails(UUID uuid, ClassLoaderSpec spec) {
        this.uuid = uuid;
        this.spec = spec;
    }

    @Override
    public String toString() {
        return "{" + getClass().getSimpleName() + " uuid: " + uuid + " spec: " + spec + " parents: " + parents + "}";
    }
}
