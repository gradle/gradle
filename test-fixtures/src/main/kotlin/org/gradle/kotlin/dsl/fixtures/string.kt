package org.gradle.kotlin.dsl.fixtures

import org.gradle.util.TextUtil


fun String.toPlatformLineSeparators() = TextUtil.toPlatformLineSeparators(this)
