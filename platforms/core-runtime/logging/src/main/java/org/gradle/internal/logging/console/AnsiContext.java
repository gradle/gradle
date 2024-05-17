/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.internal.logging.text.Style;
import org.gradle.internal.logging.text.StyledTextOutput;

public interface AnsiContext {
    /**
     * Change the ANSI color before executing the specified action. The color is reverted back after the action is executed.
     *
     * @param color the color to use
     * @param action the action to execute on ANSI with the specified color
     * @return the current context
     */
    AnsiContext withColor(ColorMap.Color color, Action<? super AnsiContext> action);

    /**
     * Change the ANSI style before executing the specified action. The style is reverted back after the action is executed.
     *
     * @param style the style to use
     * @param action the action to execute on ANSI with the specified style
     * @return the current context
     */
    AnsiContext withStyle(Style style, Action<? super AnsiContext> action);

    /**
     * Change the ANSI style before executing the specified action. The style is reverted back after the action is executed.
     *
     * @param style the style to use
     * @param action the action to execute on ANSI with the specified style
     * @return the current context
     */
    AnsiContext withStyle(StyledTextOutput.Style style, Action<? super AnsiContext> action);

    /**
     * @return the current context with the specified text written.
     */
    AnsiContext a(CharSequence value);

    /**
     * @return the current context with a new line written.
     */
    AnsiContext newLine();

    /**
     * @return the current context with the specified new line written.
     */
    AnsiContext newLines(int numberOfNewLines);

    /**
     * @return the current context with the characters moving forward from the write position erased.
     */
    AnsiContext eraseForward();

    /**
     * @return the current context with the entire line erased.
     */
    AnsiContext eraseAll();

    /**
     * @return the current context moved to the specified position.
     */
    AnsiContext cursorAt(Cursor cursor);

    /**
     * @return a new context at the specified write position.
     */
    AnsiContext writeAt(Cursor writePos);
}
