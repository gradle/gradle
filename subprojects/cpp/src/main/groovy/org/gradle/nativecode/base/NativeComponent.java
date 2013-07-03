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
package org.gradle.nativecode.base;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.LanguageSourceSet;

/**
 * Represents a logical software component, which may be built in a number of variant binaries.
 */
@Incubating
public interface NativeComponent extends Named {

    /**
     * The source sets that are used to build this component.
     */
    DomainObjectSet<LanguageSourceSet> getSource();

    /**
     * Adds a functional source set to use to compile this binary.
     * All {@link LanguageSourceSet}s for this {@link org.gradle.language.base.FunctionalSourceSet} will be added.
     */
    void source(FunctionalSourceSet sourceSet);

    /**
     * Adds some source to use to build this component.
     */
    void source(LanguageSourceSet sourceSet);

    /**
     * The binaries that are built for this component. You can use this to configure the binaries for this component.
     */
    DomainObjectSet<NativeBinary> getBinaries();

    /**
     * The name that is used to construct the output file names when building this component.
     */
    String getBaseName();

    /**
     * Sets the name that is used to construct the output file names when building this component.
     */
    void setBaseName(String baseName);
}
