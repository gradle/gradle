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

package org.gradle.internal.logging.text;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;

/**
 * Defines color configuration for navigation bar levels in Gradle console output.
 * Each level in the navigation hierarchy can have a distinct color to improve visual distinction.
 *
 * @since 8.7
 */
@Incubating
@NullMarked
public class NavigationBarColors {
    private static final String ESC = new String(new char[] { (char) 27 });
    private static final String RESET = ESC + "[0m";

    // Default ANSI color codes for different levels
    private static final String[] DEFAULT_COLORS = {
        ESC + "[36m", // Cyan for root level
        ESC + "[32m", // Green for first level
        ESC + "[33m", // Yellow for second level
        ESC + "[35m", // Magenta for third level
        ESC + "[34m"  // Blue for fourth level and beyond
    };

    private final String[] levelColors;

    /**
     * Creates a new instance with default colors.
     */
    public NavigationBarColors() {
        this.levelColors = DEFAULT_COLORS;
    }

    /**
     * Creates a new instance with custom ANSI color codes.
     *
     * @param colors Array of ANSI color codes for each level
     * @throws IllegalArgumentException if colors array is empty
     */
    public NavigationBarColors(String[] colors) {
        if (colors == null || colors.length == 0) {
            throw new IllegalArgumentException("At least one color must be specified");
        }
        this.levelColors = colors;
    }

    /**
     * Gets the color for the specified navigation level.
     *
     * @param level The navigation level (0-based)
     * @return ANSI color code for the level
     */
    public String getColorForLevel(int level) {
        if (level < 0) {
            return DEFAULT_COLORS[0];
        }
        return levelColors[Math.min(level, levelColors.length - 1)];
    }

    /**
     * Wraps text with the appropriate color for the specified level.
     *
     * @param text The text to color
     * @param level The navigation level (0-based)
     * @return The colored text
     */
    public String colorize(String text, int level) {
        return getColorForLevel(level) + text + RESET;
    }
} 