/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.file;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;

import java.util.Map;

/**
 * Additional configuration parameters for {@link ContentFilterable#expand(Map, Action)} action.
 *
 * @since 7.2
 */
@Incubating
public interface ExpandDetails {
    /**
     * Controls if the underlying {@link groovy.text.SimpleTemplateEngine} escapes backslashes in the file before processing. If this is set to {@code false} then escape sequences in the processed
     * files ({@code \n}, {@code \t}, {@code \\}, etc) are converted to the symbols that they represent, so, for example {@code \n} becomes newline. If set to {@code true} then escape sequences are
     * left as is.
     * <p>
     * Default value is {@code false}.
     *
     * @see groovy.text.SimpleTemplateEngine#setEscapeBackslash(boolean)
     */
    Property<Boolean> getEscapeBackslash();
}
