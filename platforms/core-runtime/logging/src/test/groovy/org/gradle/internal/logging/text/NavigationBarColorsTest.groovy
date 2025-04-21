/*
 * Copyright 2024 Gradle and contributors.
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

package org.gradle.internal.logging.text

import spock.lang.Specification
import spock.lang.Subject

class NavigationBarColorsTest extends Specification {
    @Subject
    NavigationBarColors colors

    def "uses default colors when no custom colors provided"() {
        given:
        colors = new NavigationBarColors()

        expect:
        colors.getColorForLevel(0).contains("[36m") // Cyan
        colors.getColorForLevel(1).contains("[32m") // Green
        colors.getColorForLevel(2).contains("[33m") // Yellow
        colors.getColorForLevel(3).contains("[35m") // Magenta
        colors.getColorForLevel(4).contains("[34m") // Blue
    }

    def "accepts custom colors"() {
        given:
        def customColors = ["\u001B[31m", "\u001B[32m"] as String[] // Red, Green
        colors = new NavigationBarColors(customColors)

        expect:
        colors.getColorForLevel(0) == "\u001B[31m"
        colors.getColorForLevel(1) == "\u001B[32m"
        colors.getColorForLevel(2) == "\u001B[32m" // Uses last color for overflow
    }

    def "handles negative levels by using first color"() {
        given:
        colors = new NavigationBarColors()

        expect:
        colors.getColorForLevel(-1).contains("[36m") // Cyan (first color)
    }

    def "colorizes text with level-appropriate color"() {
        given:
        colors = new NavigationBarColors()
        def text = "Test"

        when:
        def colorized = colors.colorize(text, 1)

        then:
        colorized.startsWith("\u001B[32m") // Green
        colorized.endsWith("\u001B[0m")
        colorized.contains(text)
    }

    def "throws exception for null or empty colors array"() {
        when:
        new NavigationBarColors(colors as String[])

        then:
        thrown(IllegalArgumentException)

        where:
        colors << [null, []]
    }
} 