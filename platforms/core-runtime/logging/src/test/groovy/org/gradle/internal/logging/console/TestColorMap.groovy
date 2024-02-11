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
import org.gradle.internal.logging.text.Style
import org.gradle.internal.logging.text.StyledTextOutput

class TestColorMap implements ColorMap {
    def Ansi.Attribute statusBarOn = Ansi.Attribute.INTENSITY_BOLD
    def Ansi.Attribute statusBarOff = Ansi.Attribute.RESET

    ColorMap.Color getStatusBarColor() {
        if (statusBarOn == Ansi.Attribute.RESET) {
            return {} as ColorMap.Color
        }
        return [on : { ansi -> ansi.a(statusBarOn) },
                off: { ansi -> ansi.a(statusBarOff) }
        ] as ColorMap.Color
    }

    ColorMap.Color getColourFor(StyledTextOutput.Style style) {
        if (style != StyledTextOutput.Style.Header) {
            return {} as ColorMap.Color
        }
        return [on : { ansi -> ansi.fg(Ansi.Color.YELLOW) },
                off: { ansi -> ansi.fg(Ansi.Color.DEFAULT) }
        ] as ColorMap.Color
    }

    @Override
    ColorMap.Color getColourFor(Style style) {
        return null
    }
}
