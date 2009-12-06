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

public class ScalaDocOptions extends AbstractOptions {

    /**
     * Generate deprecation information.
     */
    boolean deprecation = true

    /**
     * Generate unchecked information.
     */
    boolean unchecked = true

    /**
     * Text to appear in the window title.
     */
    String windowTitle

    /**
     * Html text to appear in the main frame title.
     */
    String docTitle

    /**
     * Html text to appear in the header for each page.
     */
    String header

    /**
     * Html text to appear in the footer for each page.
     */
    String footer

    /**
     * Html text to appear in the top text for each page.
     */
    String top

    /**
     * Html text to appear in the bottom text for each page.
     */
    String bottom

    /**
     * Style sheet to override default style.
     */
    File styleSheet

    /**
     * Additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     */
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
