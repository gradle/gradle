/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.groovy.scripts;

import java.io.File;

/**
 * Handles mapping a build script class name to the build script source file.
 * The way in which this mapping is performed in implementation specific, but
 * the main requirement is that a given mapping must remain valid during an
 * execution of Gradle.  There is also some need for persistence of the data
 * since it is expected that an external source may call these methods outside
 * of the context of a Gradle execution.  In this case, a best effort should
 * be made to perserve these mappings for the next execution of Gradle.
 * @author jmurph
 */
public interface ScriptSourceMappingHandler
{
    /**
     * Returns the source for the given class name.  If the class name is not
     * known to be associated with any source build script, a null is returned.
     *
     * @param  buildScriptClassName The class name of the script.
     * @return The file object representing the source script, or null.  Note
     *         that the resultant File object might not represent a valid file
     *         in the filesystem.
     * @throws NullPointerException If the <code>buildScriptClassName</code> argument is null.
     */
    File getSource(String buildScriptClassName);

    /**
     * Adds a mapping for the given ScriptSource.  The implies immediately
     * persisting this information so that other processes can detect the new
     * information.
     *
     * @param  source     The script for which to store a mapping.
     * @throws NullPointerException If the <code>source</code> argument is null.
     */
    void addSource(ScriptSource source);
}
