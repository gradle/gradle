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
package org.gradle.api.tasks.scala

import org.gradle.api.tasks.compile.AbstractOptions
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.InputFile

public class ScalaDocOptions extends AbstractOptions {

    /**
     * Generate deprecation information.
     */
    @Input
    boolean deprecation = true

    /**
     * Generate unchecked information.
     */
    @Input
    boolean unchecked = true

    /**
     * Text to appear in the window title.
     */
    @Input @Optional
    String windowTitle

    /**
     * Html text to appear in the main frame title.
     */
    @Input @Optional
    String docTitle

    /**
     * Html text to appear in the header for each page.
     */
    @Input @Optional
    String header

    /**
     * Html text to appear in the footer for each page.
     */
    @Input @Optional
    String footer

    /**
     * Html text to appear in the top text for each page.
     */
    @Input @Optional
    String top

    /**
     * Html text to appear in the bottom text for each page.
     */
    @Input @Optional
    String bottom

    /**
     * Style sheet to override default style.
     */
    @InputFile @Optional
    File styleSheet

    /**
     * Additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     */
    @Input @Optional
    List<String> additionalParameters


    Map fieldName2AntMap() {
        [
                additionalParameters: 'addParams'
        ]
    }

    Map fieldValue2AntMap() {
        [
                deprecation: {toOnOffString(deprecation)},
                unchecked: {toOnOffString(unchecked)},
                additionalParameters: {additionalParameters.isEmpty() ? ' ' : additionalParameters.join(' ')},
        ]
    }

    private String toOnOffString(value) {
        return value ? 'on' : 'off'
    }

}
