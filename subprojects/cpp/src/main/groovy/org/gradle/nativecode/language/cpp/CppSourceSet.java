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
package org.gradle.nativecode.language.cpp;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.nativecode.base.DependentSourceSet;
import org.gradle.nativecode.base.HeaderExportingSourceSet;

/**
 * A representation of a unit of C++ source.
 */
@Incubating
public interface CppSourceSet extends HeaderExportingSourceSet, LanguageSourceSet, DependentSourceSet {

    // TODO:DAZ This doesn't feel right. Need some better modelling.
    /**
     * A unique name for this source set across all functional source sets.
     */
    String getFullName();

    /**
     * The headers.
     */
    CppSourceSet exportedHeaders(Closure closure);

    /**
     * The source.
     */
    CppSourceSet source(Closure closure);
}