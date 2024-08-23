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

import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import javax.inject.Inject;

/**
 * Options for the ScalaDoc tool.
 */
@SuppressWarnings("deprecation")
public abstract class ScalaDocOptions extends org.gradle.api.tasks.compile.AbstractOptions {

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

    @Internal
    @Deprecated
    public Property<Boolean> getIsDeprecation() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsDeprecation()", "getDeprecation()");
        return getDeprecation();
    }

    /**
     * Tells whether to generate unchecked information.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getUnchecked();

    @Internal
    @Deprecated
    public Property<Boolean> getIsUnchecked() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsUnchecked()", "getUnchecked()");
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
     * Returns the HTML text to appear in the main frame title.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getDocTitle();

    /**
     * Returns the HTML text to appear in the header for each page.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getHeader();

    /**
     * Returns the HTML text to appear in the footer for each page.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getFooter();

    /**
     * Returns the HTML text to appear in the top text for each page.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getTop();

    /**
     * Returns the HTML text to appear in the bottom text for each page.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getBottom();

    /**
     * Returns the additional parameters passed to the compiler.
     * Each parameter starts with '-'.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract ListProperty<String> getAdditionalParameters();
}
