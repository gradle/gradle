/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.gosu;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.compile.AbstractOptions;

/**
 * Options for the GosuDoc tool.
 */
public class GosuDocOptions extends AbstractOptions {

    private boolean verbose;
    private String title;

    /**
     * Tells whether to produce verbose output
     */
    @Input
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether to produce verbose output
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the HTML text to appear in the main frame title.
     * @return the HTML text to appear in the main frame title.
     */
    @Input
    @Optional
    public String getTitle() {
        return title;
    }

    /**
     * Sets the HTML text to appear in the main frame title.
     * @param title the HTML text to appear in the main frame title.
     */
    public void setTitle(String title) {
        this.title = title;
    }
}
