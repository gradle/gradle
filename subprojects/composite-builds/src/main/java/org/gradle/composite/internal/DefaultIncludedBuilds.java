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

package org.gradle.composite.internal;

import com.google.common.collect.Maps;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.initialization.IncludedBuilds;

import java.util.Map;

public class DefaultIncludedBuilds implements IncludedBuilds {
    private final Map<String, IncludedBuild> builds = Maps.newHashMap();

    public void registerBuild(IncludedBuild build) {
        builds.put(build.getName(), build);
    }

    @Override
    public Iterable<IncludedBuild> getBuilds() {
        return builds.values();
    }

    @Override
    public IncludedBuild getBuild(String name) {
        return builds.get(name);
    }
}
