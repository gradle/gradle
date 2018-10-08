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
package org.gradle.api.tasks.scala;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.compile.AbstractOptions;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Options for the ScalaDoc tool.
 */
public class ScalaDocOptions extends AbstractOptions {
    private boolean deprecation = true;
    private boolean unchecked = true;
    private String windowTitle;
    private String docTitle;
    private String header;
    private String footer;
    private String top;
    private String bottom;
    private List<String> additionalParameters;

    /**
     * Tells whether to generate deprecation information.
     */
    @Input
    public boolean isDeprecation() {
        return deprecation;
    }

    /**
     * Sets whether to generate deprecation information.
     */
    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    /**
     * Tells whether to generate unchecked information.
     */
    @Input
    public boolean isUnchecked() {
        return unchecked;
    }

    /**
     * Sets whether to generate unchecked information.
     */
    public void setUnchecked(boolean unchecked) {
        this.unchecked = unchecked;
    }

    /**
     * Returns the text to appear in the window title.
     */
    @Nullable @Optional @Input
    public String getWindowTitle() {
        return windowTitle;
    }

    /**
     * Sets the text to appear in the window title.
     */
    public void setWindowTitle(@Nullable String windowTitle) {
        this.windowTitle = windowTitle;
    }

    /**
     * Returns the HTML text to appear in the main frame title.
     */
    @Nullable @Optional @Input
    public String getDocTitle() {
        return docTitle;
    }

    /**
     * Sets the HTML text to appear in the main frame title.
     */
    public void setDocTitle(@Nullable String docTitle) {
        this.docTitle = docTitle;
    }

    /**
     * Returns the HTML text to appear in the header for each page.
     */
    @Nullable @Optional @Input
    public String getHeader() {
        return header;
    }

    /**
     * Sets the HTML text to appear in the header for each page.
     */
    public void setHeader(@Nullable String header) {
        this.header = header;
    }

    /**
     * Returns the HTML text to appear in the footer for each page.
     */
    @Nullable @Optional @Input
    public String getFooter() {
        return footer;
    }

    /**
     * Sets the HTML text to appear in the footer for each page.
     */
    public void setFooter(@Nullable String footer) {
        this.footer = footer;
    }

    /**
     * Returns the HTML text to appear in the top text for each page.
     */
    @Nullable @Optional @Input
    public String getTop() {
        return top;
    }

    /**
     * Sets the HTML text to appear in the top text for each page.
     */
    public void setTop(@Nullable String top) {
        this.top = top;
    }

    /**
     * Returns the HTML text to appear in the bottom text for each page.
     */
    @Nullable @Optional @Input
    public String getBottom() {
        return bottom;
    }

    /**
     * Sets the HTML text to appear in the bottom text for each page.
     */
    public void setBottom(@Nullable String bottom) {
        this.bottom = bottom;
    }

    /**
     * Returns the additional parameters passed to the compiler.
     * Each parameter starts with '-'.
     */
    @Nullable @Optional @Input
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    /**
     * Sets the additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     */
    public void setAdditionalParameters(@Nullable List<String> additionalParameters) {
        this.additionalParameters = additionalParameters;
    }

    @Override
    protected String getAntPropertyName(String fieldName) {
        if (fieldName.equals("additionalParameters")) {
            return "addparams";
        }
        return fieldName;
    }

    @Override
    protected Object getAntPropertyValue(String fieldName, Object value) {
        if (fieldName.equals("deprecation")) {
            return toOnOffString(deprecation);
        }
        if (fieldName.equals("unchecked")) {
            return toOnOffString(unchecked);
        }
        if (fieldName.equals("additionalParameters")) {
            return CollectionUtils.asCommandLine(getAdditionalParameters());
        }
        return value;
    }

    private String toOnOffString(boolean value) {
        return value ? "on" : "off";
    }

}
