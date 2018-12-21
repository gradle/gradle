package org.gradle.kotlin.dsl.fixtures

import org.gradle.kotlin.dsl.support.normaliseLineSeparators

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matcher


fun containsMultiLineString(string: String): Matcher<String> =
    containsString(string.trimIndent().normaliseLineSeparators())


fun equalToMultiLineString(string: String): Matcher<String> =
    equalTo(string.normaliseLineSeparators())
