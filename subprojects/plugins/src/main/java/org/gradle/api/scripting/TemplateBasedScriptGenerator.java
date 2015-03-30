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

package org.gradle.api.scripting;

import org.gradle.api.Incubating;

import java.io.Reader;
import java.util.Map;

/**
 * Interface for generating scripts with the provided details based on a provided template.
 *
 * @param <T> Script generation details
 */
@Incubating
public interface TemplateBasedScriptGenerator<T extends ScriptGenerationDetails> extends ScriptGenerator<T> {
    /**
     * Sets the template reader used for generating script.
     *
     * @param reader Template reader
     */
    void setTemplate(Reader reader);

    /**
     * Gets the template reader used for generating script.
     *
     * @return Template reader
     */
    Reader getTemplate();

    /**
     * Creates a binding used for the template expression replacement.
     *
     * @param details Script generation details
     * @return Binding key and value mapping
     */
    Map<String, String> createBinding(T details);
}
