/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.api.file.CopySpec

/**
 * Assembles an EAR archive.
 *
 * @author David Gileadi
 */
class Ear extends Jar {
    public static final String EAR_EXTENSION = 'ear'

    private CopySpec lib

    Ear() {
        extension = EAR_EXTENSION

        lib = copyAction.rootSpec.addChild().into('lib')
    }

    public CopySpec getLib() {
        return lib.addChild()
    }

    /**
     * Adds dependency libraries to include in the 'lib' directory of the EAR archive.
     *
     * <p>The given closure is executed to configure a {@code CopySpec}. The {@link CopySpec} is passed to the closure
     * as its delegate.</p>
     *
     * @param configureClosure The closure.
     * @return The created {@code CopySpec}
     */
    public CopySpec lib(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getLib())
    }
}
