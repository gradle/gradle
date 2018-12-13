package org.gradle.kotlin.dsl.fixtures

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matcher


fun containsMultiLineString(string: String): Matcher<String> =
    containsString(string.trimIndent().toPlatformLineSeparators())


fun equalToMultiLineString(string: String): Matcher<String> =
    equalTo(string.toPlatformLineSeparators())
