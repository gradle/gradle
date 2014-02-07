/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.base.internal;

import org.gradle.api.Task;
import org.gradle.language.base.LanguageSourceSet;

public interface LanguageSourceSetInternal extends LanguageSourceSet {

    // TODO:DAZ This doesn't feel right. Need some better modelling.
    // TODO:DAZ Use this for ClassDirectoryBinary task names as well: need to hack the naming scheme to maintain back-compatibility
    /**
     * A unique name for this source set across all functional source sets.
     */
    String getFullName();

    /**
     * Return true if the source set contains sources, or if the source set is generated.
     */
    boolean getMayHaveSources();

    // TODO:DAZ Maybe use an extension property
    Task getGeneratorTask();
}
