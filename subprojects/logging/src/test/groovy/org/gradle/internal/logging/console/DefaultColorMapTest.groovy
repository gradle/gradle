/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.console

import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.Attribute
import org.fusesource.jansi.Ansi.Color
import org.gradle.internal.logging.text.StyledTextOutput.Style
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class DefaultColorMapTest extends Specification {
    @Rule public final SetSystemProperties sysProps = new SetSystemProperties()
    private final DefaultColorMap map = new DefaultColorMap()

    def canSetColorForAStyleUsingSystemProperty() {
        System.properties['org.gradle.color.info'] = 'green'
        Ansi ansi = Mock()

        when:
        def color = map.getColourFor(Style.Info)
        color.on(ansi)

        then:
        1 * ansi.fg(Color.GREEN)
        0 * ansi._

        when:
        color.off(ansi)

        then:
        1 * ansi.fg(Color.DEFAULT)
        0 * ansi._
    }

    def canDisableColorForAStyleUsingSystemProperty() {
        System.properties['org.gradle.color.info'] = 'default'
        Ansi ansi = Mock()

        when:
        def color = map.getColourFor(Style.Info)
        color.on(ansi)

        then:
        0 * ansi._

        when:
        color.off(ansi)

        then:
        0 * ansi._
    }

    def canSetBoldAttributeForAStyleUsingSystemProperty() {
        System.properties['org.gradle.color.info'] = 'bold'
        Ansi ansi = Mock()

        when:
        def color = map.getColourFor(Style.Info)
        color.on(ansi)

        then:
        1 * ansi.a(Attribute.INTENSITY_BOLD)
        0 * ansi._

        when:
        color.off(ansi)

        then:
        1 * ansi.a(Attribute.RESET)
        0 * ansi._
    }

    def statusBarIsBoldByDefault() {
        Ansi ansi = Mock()

        when:
        def color = map.statusBarColor
        color.on(ansi)

        then:
        1 * ansi.a(Attribute.INTENSITY_BOLD)
        0 * ansi._

        when:
        color.off(ansi)

        then:
        1 * ansi.a(Attribute.RESET)
        0 * ansi._
    }
}
