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

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Collections;

/**
 * Data object for use with version catalog generation to encode module, version and if generated aliases should be shortened or qualified.
 */
@NullMarked
public final class BuildInitDependency {
    private final String module;
    @Nullable
    private final String version;
    private final ImmutableList<DependencyExclusion> exclusions;

    private BuildInitDependency(String module, @Nullable String version, List<DependencyExclusion> exclusions) {
        this.module = module;
        this.version = version;
        this.exclusions = ImmutableList.copyOf(exclusions);
    }

    public static BuildInitDependency of(String module, String version) {
        return new BuildInitDependency(module, version, Collections.emptyList());
    }

    public static BuildInitDependency of(String group, String name, String version) {
        return new BuildInitDependency(group + ":" + name, version, Collections.emptyList());
    }

    public static BuildInitDependency of(String group, String name, String version, List<DependencyExclusion> excludes) {
        return new BuildInitDependency(group + ":" + name, version, excludes);
    }

    public static BuildInitDependency of(String module) {
        return new BuildInitDependency(module, null, Collections.emptyList());
    }

    public String getModule() {
        return module;
    }

    public @Nullable String getVersion() {
        return version;
    }

    public ImmutableList<DependencyExclusion> getExclusions() {
        return exclusions;
    }
    public String toNotation() {
        return module + (version != null ? ":" + version : "");
    }

    /**
     * Value type representing the coordinates of a dependency exclusion.
     */
    @NullMarked
    public static final class DependencyExclusion {
        private final String group;
        private final String module;

        public DependencyExclusion(String group, String module) {
            this.group = group;
            this.module = module;
        }

        public String getGroup() {
            return group;
        }

        public String getModule() {
            return module;
        }
    }
}
