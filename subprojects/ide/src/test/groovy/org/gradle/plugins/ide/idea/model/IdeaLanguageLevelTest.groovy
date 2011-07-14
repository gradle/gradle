package org.gradle.plugins.ide.idea.model

import org.gradle.api.JavaVersion
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 7/14/11
 */
class IdeaLanguageLevelTest extends Specification {

    def "formats language level in IDEA fancy format"() {
        expect:
        new IdeaLanguageLevel(JavaVersion.VERSION_1_3).formatted == null
        new IdeaLanguageLevel(JavaVersion.VERSION_1_4).formatted == "JDK_1_4"
        new IdeaLanguageLevel(JavaVersion.VERSION_1_5).formatted == "JDK_1_5"
        new IdeaLanguageLevel(JavaVersion.VERSION_1_6).formatted == "JDK_1_6"
        new IdeaLanguageLevel(JavaVersion.VERSION_1_7).formatted == "JDK_1_6"
    }
}
