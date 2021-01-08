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

package org.gradle.api.publish.maven;

import org.gradle.api.provider.Property;

/**
 * The CI management system of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 */
public interface MavenPomCiManagement {

    /**
     * The name of this CI management system.
     */
    Property<String> getSystem();

    /**
     * The URL of this CI management system.
     */
    Property<String> getUrl();

}
