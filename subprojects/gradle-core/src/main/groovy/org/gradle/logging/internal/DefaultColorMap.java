/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.logging.internal;

import org.fusesource.jansi.Ansi;
import org.gradle.logging.StyledTextOutput;

import java.util.EnumMap;
import java.util.Map;

import static org.fusesource.jansi.Ansi.Color.*;
import static org.gradle.logging.StyledTextOutput.Style.*;

public class DefaultColorMap implements ColorMap {
    private final Map<StyledTextOutput.Style, Ansi.Color> defaults = new EnumMap<StyledTextOutput.Style, Ansi.Color>(StyledTextOutput.Style.class);
    private final Map<StyledTextOutput.Style, Ansi.Color> colours = new EnumMap<StyledTextOutput.Style, Ansi.Color>(StyledTextOutput.Style.class);
    private boolean useColor = true;

    public DefaultColorMap() {
        defaults.put(Header, DEFAULT);
        defaults.put(Info, YELLOW);
        defaults.put(Description, YELLOW);
//        defaults.put(ProgressStatus, YELLOW);
        defaults.put(Identifier, GREEN);
        defaults.put(UserInput, GREEN);
//        defaults.put(Error, RED);
    }

    public void setUseColor(boolean useColor) {
        this.useColor = useColor;
    }

    public Ansi.Attribute getStatusBarOn() {
//        return Ansi.Attribute.INTENSITY_BOLD;
        return Ansi.Attribute.RESET;
    }

    public Ansi.Attribute getStatusBarOff() {
//        return Ansi.Attribute.INTENSITY_BOLD_OFF;
        return Ansi.Attribute.RESET;
    }

    public Ansi.Color getColourFor(StyledTextOutput.Style style) {
        if (!useColor) {
            return DEFAULT;
        }
        
        Ansi.Color color = colours.get(style);
        if (color == null) {
            color = getColor(style);
            colours.put(style, color);
        }
        return color;
    }

    private Ansi.Color getColor(StyledTextOutput.Style style) {
        String override = System.getProperty(String.format("org.gradle.color.%s", style.name().toLowerCase()));
        if (override != null) {
            return Ansi.Color.valueOf(override.toUpperCase());
        } else {
            Ansi.Color color = defaults.get(style);
            return color == null ? DEFAULT : color;
        }
    }
}
