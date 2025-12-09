/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Data object for use with version catalog generation to encode module, version and if generated aliases should be shortened or qualified.
 */
@NullMarked
public class BuildInitDependency {
    final String module;
    final String version;
    private final List<Exclusion> exclusions;

    private BuildInitDependency(String module, @Nullable String version, List<Exclusion> exclusions) {
        this.module = module;
        this.version = version;
        this.exclusions = exclusions;
    }

    public static BuildInitDependency of(String module, String version) {
        return new BuildInitDependency(module, version, Collections.emptyList());
    }

    public static BuildInitDependency of(String group, String name, String version) {
        return new BuildInitDependency(group + ":" + name, version, Collections.emptyList());
    }

    public static BuildInitDependency of(String module) {
        return new BuildInitDependency(module, null, Collections.emptyList());
    }

    public static BuildInitDependency of(String group, String name, String version, List<Exclusion> exclusions) {
        return new BuildInitDependency(group + ":" + name, version, exclusions);
    }

    public String toNotation() {
        return module + (version != null ? ":" + version : "");
    }

    public List<String> exclusionsToNotation() {
        List<String> result = new java.util.ArrayList<>();

        return result;
    }

    public static final class Exclusion {
        public final String group;
        public final String module;

        public Exclusion(String group, String module) {
            this.group = group;
            this.module = module;
        }
    }
}
