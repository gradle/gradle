/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.idea.model;

import com.google.common.base.Objects;
import org.gradle.api.JavaVersion;

/**
 * Java language level used by IDEA projects.
 */
public class IdeaLanguageLevel {

    private String level;

    public IdeaLanguageLevel(Object version) {
        if (version != null && version instanceof String && ((String) version).startsWith("JDK_")) {
            level = (String) version;
            return;
        }
        level = JavaVersion.toVersion(version).name().replaceFirst("VERSION", "JDK");
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        IdeaLanguageLevel that = (IdeaLanguageLevel) o;
        return Objects.equal(level, that.level);
    }

    @Override
    public int hashCode() {
        return level != null ? level.hashCode() : 0;
    }
}
