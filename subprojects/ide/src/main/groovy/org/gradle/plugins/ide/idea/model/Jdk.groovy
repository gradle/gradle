/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

/**
 * Represents information for the project Java SDK. This translates to attributes of the ProjectRootManager
 * element in the ipr.
 */
class Jdk {
    boolean assertKeyword
    boolean jdk15 = false
    String languageLevel
    String projectJdkName

    def Jdk(String jdkName, IdeaLanguageLevel ideaLanguageLevel) {
        if (jdkName.startsWith("1.4")) {
            assertKeyword = true
            jdk15 = false
        }
        else if (jdkName >= '1.5') {
            assertKeyword = true
            jdk15 = true
        }
        else {
            assertKeyword = false
        }
        languageLevel = ideaLanguageLevel.level
        projectJdkName = jdkName
    }

    def Jdk(assertKeyword, jdk15, languageLevel, projectJdkName) {
        this.assertKeyword = assertKeyword;
        this.jdk15 = jdk15;
        this.languageLevel = languageLevel
        this.projectJdkName = projectJdkName;
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Jdk jdk = (Jdk) o;

        if (assertKeyword != jdk.assertKeyword) { return false }
        if (jdk15 != jdk.jdk15) { return false }
        if (languageLevel != jdk.languageLevel) { return false }
        if (projectJdkName != jdk.projectJdkName) { return false }

        return true;
    }

    int hashCode() {
        int result;

        result = 31 * result + (assertKeyword ? 1 : 0);
        result = 31 * result + (jdk15 ? 1 : 0);
        result = 31 * result + (languageLevel != null ? languageLevel.hashCode() : 0);
        result = 31 * result + (projectJdkName != null ? projectJdkName.hashCode() : 0);
        return result;
    }


    public String toString() {
        return "Jdk{" +
                "assertKeyword=" + assertKeyword +
                ", jdk15=" + jdk15 +
                ", languageLevel='" + languageLevel +
                "', projectJdkName='" + projectJdkName + '\'' +
                '}';
    }
}
