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

package org.gradle.configuration;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Uniquely identifies the target of some configuration.
 *
 * This is primarily used to support
 * {@code ApplyScriptPluginBuildOperationType.Details} and {@code ApplyPluginBuildOperationType.Details}.
 */
public abstract class ConfigurationTargetIdentifier {

    ConfigurationTargetIdentifier() {
    }

    public enum Type {
        GRADLE,
        SETTINGS,
        PROJECT;

        public final String label = name().toLowerCase(Locale.ROOT);
    }

    public abstract Type getTargetType();

    /**
     * If type == project, that project's path (not identity path).
     * Else, null.
     */
    @Nullable
    public abstract String getTargetPath();

    public abstract String getBuildPath();

}
