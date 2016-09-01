package org.gradle.script.lang.kotlin.integration

import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher

import java.util.regex.Pattern

fun matching(pattern: String) =
    matching(pattern.toPattern())

fun matching(pattern: Pattern) =
    PatternMatcher(pattern)

class PatternMatcher(val pattern: Pattern) : TypeSafeMatcher<String>() {

    override fun matchesSafely(item: String): Boolean =
        pattern.matcher(item).matches()

    override fun describeTo(description: Description) {
       description
           .appendText("a string matching the pattern ")
           .appendValue(pattern)
    }
}
