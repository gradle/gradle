package org.gradle.kotlin.dsl.fixtures

import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import java.io.File

import java.util.regex.Pattern


fun matches(pattern: String) =
    matching(pattern)


fun matching(pattern: String) =
    matching(pattern.toPattern())


fun matching(pattern: Pattern) =
    matching<String>({ appendText("a string matching the pattern ").appendValue(pattern) }) {
        pattern.matcher(this).matches()
    }


fun fileByName(fileName: String) =
    matching<File>({ appendText("a file by the name ").appendValue(fileName) }) {
        isFile && name == fileName
    }


fun <T> matching(describe: Description.() -> Unit, match: T.() -> Boolean) =
    object : TypeSafeMatcher<T>() {
        override fun matchesSafely(item: T) = match(item)
        override fun describeTo(description: Description) = describe(description)
    }
