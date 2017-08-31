/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.Incubating;

/**
 * Contains and allows configuring component module metadata information.
 * For information and examples please see {@link org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler}
 *
 * @since 2.2
 */
@Incubating
public interface ComponentModuleMetadataDetails extends ComponentModuleMetadata {

    /**
     * Configures a replacement module for this module.
     * A real world example: 'com.google.collections:google-collections' is replaced by 'com.google.guava:guava'.
     *
     * Subsequent invocations of this method replace the previous 'replacedBy' value.
     *
     * For information and examples please see {@link org.gradle.api.artifacts.dsl.ComponentMetadataHandler}.
     *
     * @param moduleNotation a String like 'com.google.guava:guava', an instance of {@link org.gradle.api.artifacts.ModuleVersionIdentifier}, null is not permitted
     */
    void replacedBy(Object moduleNotation);
}
