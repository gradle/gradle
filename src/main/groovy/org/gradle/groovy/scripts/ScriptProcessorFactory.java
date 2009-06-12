/*
 * Copyright 2007-2008 the original author or authors.
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

import groovy.lang.Script;

/**
 * Loads scripts from text source into a {@link Script} object.
 *
 * @author Hans Dockter
 */
public interface ScriptProcessorFactory {
    /**
     * Creates a processor for the given source. The returned processor can be used to compile the script into various
     * different forms.
     *
     * @param source The script source.
     * @return a processor which can be used to process the script.
     */
    ScriptProcessor createProcessor(ScriptSource source);
}
