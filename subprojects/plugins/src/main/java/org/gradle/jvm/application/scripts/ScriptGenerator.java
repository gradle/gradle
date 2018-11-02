/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.application.scripts;

import java.io.Writer;

/**
 * Generates a script to start a JVM application.
 *
 * @see TemplateBasedScriptGenerator
 */
public interface ScriptGenerator {

    /**
     * Generate the script.
     * <p>
     * Implementations should not close the given writer.
     * It is the responsibility of the caller to close the stream.
     *
     * @param details the application details
     * @param destination the script destination
     */
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination);

}
