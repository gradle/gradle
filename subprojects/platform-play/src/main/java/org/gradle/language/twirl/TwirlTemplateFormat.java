/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.twirl;

import org.gradle.api.Incubating;

import java.util.Collection;

/**
 * Twirl Template format mapping
 *
 * @since 4.2
 */
@Incubating
public interface TwirlTemplateFormat {
    /**
     * Extension without the leading '.'.
     *
     * @return file extension
     */
    String getExtension();

    /**
     * Fully qualified class name for the template format.
     *
     * @return class name of the format
     */
    String getFormatType();

    /**
     * Imports that are needed for this template format.
     * <p>
     * These are just the packages or individual classes in Scala format.
     * Use {@code my.package._} and not {@code my.package.*}.
     * </p>
     * @return collection of imports to be added after the default Twirl imports
     */
    Collection<String> getTemplateImports();
}
