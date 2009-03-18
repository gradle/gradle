/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.groovy.scripts;

import org.gradle.api.Project;
import org.gradle.util.GUtil;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.File;

public class StringScriptSource implements ScriptSource {
    private final String description;
    private final String content;

    public StringScriptSource(String description, String content) {
        this.description = description;
        this.content = content;
    }

    public String getText() {
        if (!GUtil.isTrue(content)) {
            return null;
        }
        return content;
    }

    public String getClassName() {
        return Project.EMBEDDED_SCRIPT_ID;
    }

    public File getSourceFile() {
        return null;
    }

    public String getDisplayName() {
        return description;
    }

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
