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

package org.gradle.plugins.ide.internal.tooling.idea;

import org.gradle.tooling.model.idea.IdeaLanguageLevel;

import java.io.Serializable;

public class DefaultIdeaLanguageLevel implements IdeaLanguageLevel, Serializable {

    private final String level;

    public DefaultIdeaLanguageLevel(String level) {
        this.level = level;
    }

    public boolean isJDK_1_4() {
        return "JDK_1_4".equals(level);
    }

    public boolean isJDK_1_5() {
        return "JDK_1_5".equals(level);
    }

    public boolean isJDK_1_6() {
        return "JDK_1_6".equals(level);
    }

    public boolean isJDK_1_7() {
        return "JDK_1_7".equals(level);
    }

    public boolean isJDK_1_8() {
        return "JDK_1_8".equals(level);
    }

    public String getLevel() {
        return level;
    }

    @Override
    public String toString() {
        return "IdeaLanguageLevel{level='" + level + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultIdeaLanguageLevel)) {
            return false;
        }

        DefaultIdeaLanguageLevel that = (DefaultIdeaLanguageLevel) o;

        if (level != null ? !level.equals(that.level) : that.level != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return level != null ? level.hashCode() : 0;
    }
}