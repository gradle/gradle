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

/**
 * Represents information for the project Java SDK.
 * This translates to attributes of the ProjectRootManager element in the ipr.
 */
public class Jdk {

    private boolean assertKeyword;
    private boolean jdk15;
    private String languageLevel;
    private String projectJdkName;

    public Jdk(String jdkName, IdeaLanguageLevel ideaLanguageLevel) {
        if (jdkName.startsWith("1.4")) {
            assertKeyword = true;
            jdk15 = false;
        } else if (jdkName.compareTo("1.5") >= 0) {
            assertKeyword = true;
            jdk15 = true;
        } else {
            assertKeyword = false;
        }
        languageLevel = ideaLanguageLevel.getLevel();
        projectJdkName = jdkName;
    }

    public Jdk(boolean assertKeyword, boolean jdk15, String languageLevel, String projectJdkName) {
        this.assertKeyword = assertKeyword;
        this.jdk15 = jdk15;
        this.languageLevel = languageLevel;
        this.projectJdkName = projectJdkName;
    }

    @Deprecated
    public Jdk(Object assertKeyword, Object jdk15, Object languageLevel, Object projectJdkName) {
        this(((Boolean)assertKeyword).booleanValue(), ((Boolean)jdk15).booleanValue(), (String)languageLevel, (String)projectJdkName);
    }

    public boolean isAssertKeyword() {
        return assertKeyword;
    }

    public void setAssertKeyword(boolean assertKeyword) {
        this.assertKeyword = assertKeyword;
    }

    public boolean isJdk15() {
        return jdk15;
    }

    public void setJdk15(boolean jdk15) {
        this.jdk15 = jdk15;
    }

    public String getLanguageLevel() {
        return languageLevel;
    }

    public void setLanguageLevel(String languageLevel) {
        this.languageLevel = languageLevel;
    }

    public String getProjectJdkName() {
        return projectJdkName;
    }

    public void setProjectJdkName(String projectJdkName) {
        this.projectJdkName = projectJdkName;
    }

    @Override
    public String toString() {
        return "Jdk{"
            + "assertKeyword=" + assertKeyword
            + ", jdk15=" + jdk15
            + ", languageLevel='" + languageLevel
            + "', projectJdkName='" + projectJdkName
            + "\'" + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        Jdk jdk = (Jdk) o;
        if (assertKeyword != jdk.assertKeyword) {
            return false;
        }
        if (jdk15 != jdk.jdk15) {
            return false;
        }
        return Objects.equal(languageLevel, jdk.languageLevel)
            && Objects.equal(projectJdkName, jdk.projectJdkName);
    }

    @Override
    public int hashCode() {
        int result = 0;
        result = 31 * result + (assertKeyword ? 1 : 0);
        result = 31 * result + (jdk15 ? 1 : 0);
        result = 31 * result + (languageLevel != null ? languageLevel.hashCode() : 0);
        result = 31 * result + (projectJdkName != null ? projectJdkName.hashCode() : 0);
        return result;
    }
}
