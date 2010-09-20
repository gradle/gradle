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

import java.util.HashMap;
import java.util.Map;

import static org.fusesource.jansi.Ansi.Color.*;
import static org.gradle.logging.StyledTextOutput.Style.*;

public class DefaultColorMap implements ColorMap {
    private static final String STATUSBAR = "statusbar";
    private static final String BOLD = "bold";
    private final Map<String, String> defaults = new HashMap<String, String>();
    private final Map<String, Color> colors = new HashMap<String, Color>();
    private boolean useColor = true;
    private final Color noDecoration = new Color() {
        public void on(Ansi ansi) {
        }

        public void off(Ansi ansi) {
        }
    };
    private final Color bold = new Color() {
        public void on(Ansi ansi) {
            ansi.a(Ansi.Attribute.INTENSITY_BOLD);
        }

        public void off(Ansi ansi) {
            ansi.a(Ansi.Attribute.INTENSITY_BOLD_OFF);
        }
    };

    public DefaultColorMap() {
//        addDefault(Header, DEFAULT);
        addDefault(Info, YELLOW);
        addDefault(Description, YELLOW);
        addDefault(ProgressStatus, YELLOW);
        addDefault(Identifier, GREEN);
        addDefault(UserInput, GREEN);
        addDefault(Failure, RED);
//        addDefault(Error, RED);
        defaults.put(STATUSBAR, BOLD);
    }

    private void addDefault(StyledTextOutput.Style style, Ansi.Color color) {
        defaults.put(style.name().toLowerCase(), color.name());
    }

    public void setUseColor(boolean useColor) {
        this.useColor = useColor;
    }

    public Color getStatusBarColor() {
        return getColor(STATUSBAR);
    }

    public Color getColourFor(StyledTextOutput.Style style) {
        return getColor(style.name().toLowerCase());
    }

    private Color getColor(String name) {
        if (!useColor) {
            return noDecoration;
        }

        Color color = colors.get(name);
        if (color == null) {
            color = createColor(name);
            colors.put(name, color);
        }

        return color;
    }

    private Color createColor(String name) {
        String colorSpec = System.getProperty(String.format("org.gradle.color.%s", name), defaults.get(name));

        if (colorSpec != null) {
            if (colorSpec.equalsIgnoreCase(BOLD)) {
                String terminalProgram = System.getenv("TERM_PROGRAM");
                if (terminalProgram != null && terminalProgram.equals("iTerm.app")) {
                    // iTerm displays bold as red (by default), so don't bother
                    return noDecoration;
                }
                return bold;
            }

            Ansi.Color ansiColor = Ansi.Color.valueOf(colorSpec.toUpperCase());
            if (ansiColor != DEFAULT) {
                return new ForegroundColor(ansiColor);
            }
        }

        return noDecoration;
    }

    private static class ForegroundColor implements Color {
        private final Ansi.Color ansiColor;

        public ForegroundColor(Ansi.Color ansiColor) {
            this.ansiColor = ansiColor;
        }

        public void on(Ansi ansi) {
            ansi.fg(ansiColor);
        }

        public void off(Ansi ansi) {
            ansi.fg(DEFAULT);
        }
    }
}
