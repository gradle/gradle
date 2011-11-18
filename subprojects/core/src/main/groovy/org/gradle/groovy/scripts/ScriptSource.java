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
package org.gradle.groovy.scripts;

import org.gradle.api.internal.resource.Resource;

import java.io.Serializable;

/**
 * The source for the text of a script, with some meta-info about the script.
 */
public interface ScriptSource extends Serializable {
    /**
     * Returns the name to use for the compiled class for this script. Never returns null.
     */
    String getClassName();

    /**
     * Returns the source for this script. Never returns null.
     */
    Resource getResource();

    /**
     * Returns the file name that is inserted into the class during compilation.  For a script with a source
     * file this is the path to the file.  Never returns null.
     */
    String getFileName();

    /**
     * Returns the description for this script. Never returns null.
     */
    String getDisplayName();
}
