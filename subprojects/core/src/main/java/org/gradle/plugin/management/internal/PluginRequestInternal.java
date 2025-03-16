/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugin.management.internal;

import org.gradle.plugin.management.PluginRequest;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public interface PluginRequestInternal extends PluginRequest {

    boolean isApply();

    @Nullable
    Integer getLineNumber();

    @Nullable
    String getScriptDisplayName();

    String getDisplayName();

    @Nullable
    PluginRequest getOriginalRequest();

    Origin getOrigin();

    Optional<PluginCoordinates> getAlternativeCoordinates();

    enum Origin {
        AUTO_APPLIED,
        OTHER
    }
}
