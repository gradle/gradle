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

package org.gradle.language.twirl.internal;

import com.google.common.base.Preconditions;
import org.gradle.language.twirl.TwirlTemplateFormat;

import java.io.Serializable;
import java.util.Collection;

public class DefaultTwirlTemplateFormat implements TwirlTemplateFormat, Serializable {
    private final String extension;
    private final String formatType;
    private final Collection<String> imports;

    public DefaultTwirlTemplateFormat(String extension, String formatType, Collection<String> imports) {
        Preconditions.checkNotNull(extension, "Custom template extension cannot be null.");
        Preconditions.checkArgument(!extension.startsWith("."), "Custom template extension should not start with a dot.");
        Preconditions.checkNotNull(formatType, "Custom template format type cannot be null.");

        this.extension = extension;
        this.formatType = formatType;
        this.imports = imports;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public String getFormatType() {
        return formatType;
    }

    @Override
    public Collection<String> getTemplateImports() {
        return imports;
    }

    @Override
    public String toString() {
        return "DefaultTwirlTemplateFormat{"
            + "extension='" + extension + '\''
            + ", formatType='" + formatType + '\''
            + ", imports=" + imports
            + '}';
    }
}
