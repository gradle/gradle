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

import org.gradle.api.JavaVersion;
import org.gradle.tooling.model.idea.IdeaLanguageLevel;

import java.io.Serializable;

public class DefaultIdeaLanguageLevel implements IdeaLanguageLevel, Serializable {

    private final JavaVersion level;

    public DefaultIdeaLanguageLevel(JavaVersion level) {
        this.level = level;
    }

    public boolean isJDK_1_4() {
        return level == JavaVersion.VERSION_1_4;
    }

    public boolean isJDK_1_5() {
        return level == JavaVersion.VERSION_1_5;
    }

    public boolean isJDK_1_6() {
        return level == JavaVersion.VERSION_1_6;
    }

    public boolean isJDK_1_7() {
        return level == JavaVersion.VERSION_1_7;
    }

    public boolean isJDK_1_8() {
        return level == JavaVersion.VERSION_1_8;
    }

    public String getLevel() {
        return level.toString();
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
        return level == null ? that.level == null : level.equals(that.level);
    }

    @Override
    public int hashCode() {
        return level == null ? 0 : level.hashCode();
    }
}
