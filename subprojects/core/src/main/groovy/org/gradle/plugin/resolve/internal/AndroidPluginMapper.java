/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.resolve.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;

public class AndroidPluginMapper implements ModuleMappingPluginResolver.Mapper {
    public static final String GROUP = "com.android.tools.build";
    public static final String NAME = "gradle";
    public static final String ID = "android";

    public Dependency map(PluginRequest request, DependencyHandler dependencyHandler) {
        if (request.getId().equals(ID)) {
            String version = request.getVersion();
            if (version == null) {
                throw new InvalidPluginRequestException("The '" + ID + "' plugin requires a version");
            }
            return dependencyHandler.create(StringUtils.join(new Object[]{GROUP, NAME, version}, ":"));
        } else {
            return null;
        }
    }
}
