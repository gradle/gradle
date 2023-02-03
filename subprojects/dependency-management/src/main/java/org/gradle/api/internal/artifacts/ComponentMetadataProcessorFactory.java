/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts;

/**
 * A factory for {@link ComponentMetadataProcessor}.
 * <p>
 * In a build, {@link org.gradle.api.artifacts.ComponentMetadataRule component metadata rules} can be added to transform dependencies metadata.
 * These are registered with the {@link org.gradle.api.artifacts.dsl.ComponentMetadataHandler ComponentMetadataHandler} which does not have contextual information,
 * such as the repository from which the dependency comes from.
 * <p>
 * The {@link MetadataResolutionContext} enables a {@link ComponentMetadataProcessor} to execute with the proper context.
 */
public interface ComponentMetadataProcessorFactory {

    /**
     * Creates a contextual {@link ComponentMetadataProcessor}
     *
     * @param resolutionContext the provided context
     * @return a {@code ComponentMetadataProcessor}
     */
    ComponentMetadataProcessor createComponentMetadataProcessor(MetadataResolutionContext resolutionContext);
}
