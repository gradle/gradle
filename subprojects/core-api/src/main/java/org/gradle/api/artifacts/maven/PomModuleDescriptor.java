/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.artifacts.maven;

/**
 * The metadata about a Maven POM that acts as an input to a component metadata rule.
 *
 * @since 6.3
 */
public interface PomModuleDescriptor {

    /**
     * Returns the packaging for this POM.
     *
     * @return the packaging type
     */
    String getPackaging();

}
