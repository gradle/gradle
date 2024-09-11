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
import java.util.Locale;
import java.util.Map;

import static org.fusesource.jansi.Ansi.Attribute;
import static org.fusesource.jansi.Ansi.Color.DEFAULT;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Error;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Failure;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.FailureHeader;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.ProgressStatus;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Success;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.SuccessHeader;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

public class DefaultColorMap implements ColorMap {
    private static final String STATUS_BAR = "statusbar";
    private static final String BOLD = "bold";
    private static final String COLOR_DIVIDER = "-";

    /**
     * Maps a {@link StyledTextOutput.Style} to the default color spec (that can be overridden by system properties)
     */
    private final Map<String, String> defaults = new HashMap<String, String>();

    /**
     * Maps a {@link StyledTextOutput.Style} to the {@link org.gradle.internal.logging.console.ColorMap.Color} that has been created for it
     */
    private final Map<String, Color> colorByStyle = new HashMap<String, Color>();

    /**
     * Maps a color spec to the {@link org.gradle.internal.logging.console.ColorMap.Color} that has been created for it
     */
    private final Map<String, Color> colorBySpec = new HashMap<String, Color>();

    private final Color noDecoration = new Color() {
        @Override
        public void on(Ansi ansi) {
        }

        @Override
        public void off(Ansi ansi) {
        }
    };

    public DefaultColorMap() {
        addDefault(Info, "yellow");
        addDefault(Error, "default");
        addDefault(Header, "bold");
        addDefault(Description, "yellow");
        addDefault(ProgressStatus, "yellow");
        addDefault(Identifier, "green");
        addDefault(UserInput, "bold");
        addDefault(Success, "green");
        addDefault(SuccessHeader, Success, Header);
        addDefault(Failure, "red");
        addDefault(FailureHeader, Failure, Header);
        addDefault(STATUS_BAR, "bold");
    }


    private void addDefault(StyledTextOutput.Style style, String colorSpec) {
        addDefault(style.name().toLowerCase(Locale.ROOT), colorSpec);
    }

    private void addDefault(String style, String color) {
        defaults.put(style, color);
    }

    private void addDefault(StyledTextOutput.Style style, StyledTextOutput.Style... styles) {
        String colorSpec = getColorSpecForStyle(styles[0]);
        for (int i = 1; i < styles.length; i++) {
            colorSpec += COLOR_DIVIDER + getColorSpecForStyle(styles[i]);
        }
        addDefault(style.name().toLowerCase(Locale.ROOT), colorSpec);
    }

    @Override
    public Color getStatusBarColor() {
        return getColor(STATUS_BAR);
    }

    @Override
    public Color getColourFor(StyledTextOutput.Style style) {
        return getColor(style.name().toLowerCase(Locale.ROOT));
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
            Ansi.Color ansiColor = Ansi.Color.valueOf(style.getColor().name().toUpperCase(Locale.ROOT));
            if (ansiColor != DEFAULT) {
                colors.add(new ForegroundColor(ansiColor));
            }
        }

        return new CompositeColor(colors);
    }

    private Color getColor(String style) {
        Color color = colorByStyle.get(style);
        if (color == null) {
            color = createColor(style);
            colorByStyle.put(style, color);
        }

        return color;
    }

    private String getColorSpecForStyle(StyledTextOutput.Style style) {
        return getColorSpecForStyle(style.name().toLowerCase(Locale.ROOT));
    }

    private String getColorSpecForStyle(String style) {
        return System.getProperty("org.gradle.color." + style, defaults.get(style));
    }

    private Color createColor(String style) {
        String colorSpec = getColorSpecForStyle(style);

        Color color = noDecoration;
        if (colorSpec != null) {
            color = createColorFromSpec(colorSpec);
            colorBySpec.put(colorSpec, color);
        }

        return color;
    }

    private Color createColorFromSpec(String colorSpec) {
        Color cachedColor = colorBySpec.get(colorSpec);
        if (cachedColor != null) {
            return cachedColor;
        }

        if (colorSpec.equalsIgnoreCase(BOLD)) {
            return newBoldColor();
        }
        if (colorSpec.equalsIgnoreCase("reverse")) {
            return newReverseColor();
        }
        if (colorSpec.equalsIgnoreCase("italic")) {
            return newItalicColor();
        }

        if (colorSpec.contains("-")) {
            String[] colors = colorSpec.split("-");
            ArrayList<Color> colorList = new ArrayList<Color>(colors.length);
            for (String color : colors) {
                colorList.add(createColorFromSpec(color));
            }
            return new CompositeColor(colorList);
        }

        Ansi.Color ansiColor = Ansi.Color.valueOf(colorSpec.toUpperCase(Locale.ROOT));
        if (ansiColor != DEFAULT) {
            return new ForegroundColor(ansiColor);
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

        @Override
        public void on(Ansi ansi) {
            ansi.fgBright(ansiColor);
        }

        @Override
        public void off(Ansi ansi) {
            ansi.fg(DEFAULT);
        }
    }

    private static class ForegroundColor implements Color {
        private final Ansi.Color ansiColor;

        public ForegroundColor(Ansi.Color ansiColor) {
            this.ansiColor = ansiColor;
        }

        @Override
        public void on(Ansi ansi) {
            ansi.fg(ansiColor);
        }

        @Override
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

        @Override
        public void on(Ansi ansi) {
            ansi.a(on);
        }

        @Override
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
