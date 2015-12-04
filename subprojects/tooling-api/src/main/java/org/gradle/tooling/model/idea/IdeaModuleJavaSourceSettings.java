/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.model.idea;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.java.JavaSourceSettings;

/**
 * Describes Java source settings for an an IDEA module.
 *
 * @since 2.11
 */
@Incubating
public interface IdeaModuleJavaSourceSettings extends JavaSourceSettings {

    /**
     * Returns {@code true} if the module language level is inherited from the project source settings.
     *
     * @return whether the language level is inherited
     */
    boolean isSourceLanguageLevelInherited();

    /**
     * Returns {@code true} if the bytecode level is inherited from the project settings.
     *
     * @return whether the target bytecode level is inherited
     */
    boolean isTargetBytecodeLevelInherited();

    /**
     * Returns {@code true} if the target runtime setting for the idea module is inherited from the project source settings.
     *
     * @return whether the target runtime is inherited
     */
    boolean isTargetRuntimeInherited();
}
