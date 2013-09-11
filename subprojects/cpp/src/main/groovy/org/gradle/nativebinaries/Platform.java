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

package org.gradle.nativebinaries;

import org.gradle.api.Named;

/**
 * A target platform for building native binaries.
 */
public interface Platform extends Named {

    /**
     * The cpu architecture being targeted.
     */
    Architecture getArchitecture();

    /**
     * Sets the cpu architecture being targeted.
     */
    void setArchitecture(Architecture architecture);

    /**
     * The set of available architectures.
     */
    // TODO:DAZ Model this whole concept better: don't use an enum, make this an 'architecture specification', ...
    enum Architecture { CURRENT, I386, AMD64 }
}
