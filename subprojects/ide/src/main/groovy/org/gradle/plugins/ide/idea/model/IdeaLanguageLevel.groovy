package org.gradle.plugins.ide.idea.model

import org.gradle.api.JavaVersion

/**
 * Java language level used by IDEA projects
 *
 * @author: Szczepan Faber, created at: 7/14/11
 */
class IdeaLanguageLevel {

    String formatted

    IdeaLanguageLevel(JavaVersion version) {
        if (version.toString().startsWith("1.4")) {
            formatted = 'JDK_1_4'
        }
        else if (version.toString().startsWith("1.5")) {
            formatted = 'JDK_1_5'
        }
        else if (version.toString() >= '1.6') {
            formatted = 'JDK_1_6'
        }
    }
}