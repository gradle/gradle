/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.nativ.model;

import org.gradle.api.Named;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * A {@code NativeSourceSet} represents a logical group of source that is compiled together.
 */
public interface NativeSourceSet extends NamedDomainObjectContainer<SourceDirectorySet>, Named {

    /**
     * Returns the name of this source set.
     *
     * @return The name. Never returns null.
     */
    String getName();
    
    /**
     * A more user friendly name
     */
    String getDisplayName();

}