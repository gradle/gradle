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


import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.List;


/**
 * Options for the ScalaDoc tool.
 */
public abstract class ScalaDocOptions implements Serializable {

    @Inject
    public ScalaDocOptions() {
        getDeprecation().convention(true);
        getUnchecked().convention(true);
    }

    /**
     * Tells whether to generate deprecation information.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getDeprecation();

    /**
     * Sets whether to generate deprecation information.
     */
    public void setDeprecation(boolean deprecation) {
        getDeprecation().set(deprecation);
    }

    @Internal
    public Property<Boolean> getIsDeprecation() {
        return getDeprecation();
    }

    /**
     * Tells whether to generate unchecked information.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getUnchecked();

    /**
     * Sets whether to generate unchecked information.
     */
    public void setUnchecked(boolean unchecked) {
        getUnchecked().set(unchecked);
    }

    @Internal
    public Property<Boolean> getIsUnchecked() {
        return getUnchecked();
    }

    /**
     * Returns the text to appear in the window title.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getWindowTitle();

    /**
     * Sets the text to appear in the window title.
     */
    public void setWindowTitle(@Nullable String windowTitle) {
        getWindowTitle().set(windowTitle);
    }

    /**
     * Returns the HTML text to appear in the main frame title.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getDocTitle();

    /**
     * Sets the HTML text to appear in the main frame title.
     */
    public void setDocTitle(@Nullable String docTitle) {
        getDocTitle().set(docTitle);
    }

    /**
     * Returns the HTML text to appear in the header for each page.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getHeader();

    /**
     * Sets the HTML text to appear in the header for each page.
     */
    public void setHeader(@Nullable String header) {
        getHeader().set(header);
    }

    /**
     * Returns the HTML text to appear in the footer for each page.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getFooter();

    /**
     * Sets the HTML text to appear in the footer for each page.
     */
    public void setFooter(@Nullable String footer) {
        getFooter().set(footer);
    }

    /**
     * Returns the HTML text to appear in the top text for each page.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getTop();

    /**
     * Sets the HTML text to appear in the top text for each page.
     */
    public void setTop(@Nullable String top) {
        getTop().set(top);
    }

    /**
     * Returns the HTML text to appear in the bottom text for each page.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getBottom();

    /**
     * Sets the HTML text to appear in the bottom text for each page.
     */
    public void setBottom(@Nullable String bottom) {
        getBottom().set(bottom);
    }

    /**
     * Returns the additional parameters passed to the compiler.
     * Each parameter starts with '-'.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract ListProperty<String> getAdditionalParameters();

    /**
     * Sets the additional parameters passed to the compiler.
     * Each parameter must start with '-'.
     */
    public void setAdditionalParameters(@Nullable List<String> additionalParameters) {
        getAdditionalParameters().set(additionalParameters);
    }
}
