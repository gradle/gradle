/*
 * Copyright 2008 the original author or authors.
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
 * The source for the text of a script.
 */
public interface ScriptSource {
    /**
     * Returns the text of this script. Returns null if this script has no text.
     */
    String getText();

    /**
     * Returns the name to use for the compiled class for this script. Never returns null.
     */
    String getClassName();

    /**
     * Returns the source file for this script, if any. Returns null if there is no source file for this script.
     */
    File getSourceFile();

    /**
     * Returns the description for this script. Never returns null.
     */
    String getDisplayName();
}
