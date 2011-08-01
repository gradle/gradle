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

import static org.fusesource.jansi.Ansi.Attribute;
import static org.fusesource.jansi.Ansi.Attribute.*;
import static org.fusesource.jansi.Ansi.Attribute.ITALIC;
import static org.fusesource.jansi.Ansi.Color.*;
import static org.gradle.logging.StyledTextOutput.Style.*;
import static org.gradle.logging.StyledTextOutput.Style.Success;

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

    public DefaultColorMap() {
        addDefault(Info, "yellow");
        addDefault(Error, "default");
        addDefault(Header, "default");
        addDefault(Description, "yellow");
        addDefault(ProgressStatus, "yellow");
        addDefault(Identifier, "green");
        addDefault(UserInput, "bold");
        addDefault(Success, "default");
        addDefault(Failure, "red");
        addDefault(STATUSBAR, "bold");
    }

    private void addDefault(StyledTextOutput.Style style, String color) {
        addDefault(style.name().toLowerCase(), color);
    }

    private void addDefault(String style, String color) {
        defaults.put(style, color);
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

    private Color getColor(String style) {
        if (!useColor) {
            return noDecoration;
        }

        Color color = colors.get(style);
        if (color == null) {
            color = createColor(style);
            colors.put(style, color);
        }

        return color;
    }

    private Color createColor(String style) {
        String colorSpec = System.getProperty(String.format("org.gradle.color.%s", style), defaults.get(style));

        if (colorSpec != null) {
            if (colorSpec.equalsIgnoreCase(BOLD)) {
                return new AttributeColor(INTENSITY_BOLD, INTENSITY_BOLD_OFF);
            }
            if (colorSpec.equalsIgnoreCase("reverse")) {
                return new AttributeColor(NEGATIVE_ON, NEGATIVE_OFF);
            }
            if (colorSpec.equalsIgnoreCase("italic")) {
                return new AttributeColor(ITALIC, ITALIC_OFF);
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

    private static class AttributeColor implements Color {
        private final Ansi.Attribute on;
        private final Ansi.Attribute off;

        public AttributeColor(Attribute on, Attribute off) {
            this.on = on;
            this.off = off;
        }

        public void on(Ansi ansi) {
            ansi.a(on);
        }

        public void off(Ansi ansi) {
            ansi.a(off);
        }
    }
}
