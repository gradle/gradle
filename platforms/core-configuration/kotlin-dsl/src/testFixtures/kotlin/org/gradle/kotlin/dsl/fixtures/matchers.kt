package org.gradle.kotlin.dsl.fixtures

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matcher


fun containsMultiLineString(string: String): Matcher<String> =
    containsString(string.trimIndent())
