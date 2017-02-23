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

package org.gradle.internal.logging.console;

import com.google.common.collect.Lists;
import org.fusesource.jansi.Ansi;
import org.gradle.internal.logging.text.Style;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.fusesource.jansi.Ansi.Attribute;
import static org.fusesource.jansi.Ansi.Color.DEFAULT;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.*;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Error;

public class DefaultColorMap implements ColorMap {
    private static final String STATUSBAR = "statusbar";
    private static final String BOLD = "bold";
    private final Map<String, String> defaults = new HashMap<String, String>();
    private final Map<String, Color> colors = new HashMap<String, Color>();
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

    public Color getStatusBarColor() {
        return getColor(STATUSBAR);
    }

    public Color getColourFor(StyledTextOutput.Style style) {
        return getColor(style.name().toLowerCase());
    }

    @Override
    public Color getColourFor(Style style) {
        List<Color> colors = new ArrayList<Color>();
        for (Style.Emphasis emphasis : style.getEmphasises()) {
            if (emphasis.equals(Style.Emphasis.BOLD)) {
                colors.add(newBoldColor());
            } else if (emphasis.equals(Style.Emphasis.REVERSE)) {
                colors.add(newReverseColor());
            } else if (emphasis.equals(Style.Emphasis.ITALIC)) {
                colors.add(newItalicColor());
            }
        }

        if (style.getColor().equals(Style.Color.GREY)) {
            colors.add(new BrightForegroundColor(Ansi.Color.BLACK));
        } else {
            Ansi.Color ansiColor = Ansi.Color.valueOf(style.getColor().name().toUpperCase());
            if (ansiColor != DEFAULT) {
                colors.add(new ForegroundColor(ansiColor));
            }
        }

        return new CompositeColor(colors);
    }

    private Color getColor(String style) {
        Color color = colors.get(style);
        if (color == null) {
            color = createColor(style);
            colors.put(style, color);
        }

        return color;
    }

    private Color createColor(String style) {
        String colorSpec = System.getProperty("org.gradle.color." + style, defaults.get(style));

        if (colorSpec != null) {
            if (colorSpec.equalsIgnoreCase(BOLD)) {
                return newBoldColor();
            }
            if (colorSpec.equalsIgnoreCase("reverse")) {
                return newReverseColor();
            }
            if (colorSpec.equalsIgnoreCase("italic")) {
                return newItalicColor();
            }

            Ansi.Color ansiColor = Ansi.Color.valueOf(colorSpec.toUpperCase());
            if (ansiColor != DEFAULT) {
                return new ForegroundColor(ansiColor);
            }
        }

        return noDecoration;
    }

    private static Color newBoldColor() {
        // We don't use Attribute.INTENSITY_BOLD_OFF as it's rarely supported like Windows 10
        return new AttributeColor(Attribute.INTENSITY_BOLD, Attribute.RESET);
    }

    private static Color newReverseColor() {
        return new AttributeColor(Attribute.NEGATIVE_ON, Attribute.NEGATIVE_OFF);
    }

    private static Color newItalicColor() {
        return new AttributeColor(Attribute.ITALIC, Attribute.ITALIC_OFF);
    }

    private static class BrightForegroundColor implements Color {
        private final Ansi.Color ansiColor;

        public BrightForegroundColor(Ansi.Color ansiColor) {
            this.ansiColor = ansiColor;
        }

        public void on(Ansi ansi) {
            ansi.fgBright(ansiColor);
        }

        public void off(Ansi ansi) {
            ansi.fg(DEFAULT);
        }
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

    private static class CompositeColor implements Color {
        private final List<Color> colors;

        public CompositeColor(List<Color> colors) {
            this.colors = colors;
        }

        @Override
        public void on(Ansi ansi) {
            for (Color color : colors) {
                color.on(ansi);
            }
        }

        @Override
        public void off(Ansi ansi) {
            for (Color color : Lists.reverse(colors)) {
                color.off(ansi);
            }
        }
    }
}
