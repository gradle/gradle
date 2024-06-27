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

import org.gradle.api.resources.TextResource;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

/**
 * Interface for generating scripts with the provided details based on a provided template.
 */
public interface TemplateBasedScriptGenerator extends ScriptGenerator {

    /**
     * Sets the template text resource used for generating script.
     *
     * @param template Template text resource
     */
    void setTemplate(TextResource template);

    /**
     * Gets the template reader used for generating script.
     *
     * @return Template reader
     */
    @ToBeReplacedByLazyProperty
    TextResource getTemplate();

}
