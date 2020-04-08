/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.exceptions;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.logging.text.StyledTextOutput;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A styled exception is an exception which can be rendered to the console
 * with styled text (basically colors). The only requirement is to put tags
 * between the styled elements. The tag names correspond to a {@link StyledTextOutput.Style}
 * name.</p>
 * <p>For example: This is an &lt;Failure&gt;error&lt;/Failure&gt;</p>
 * <p>It's worth noting that {@link #getMessage()} would return a non-rendered text,
 * so that tooling using the exception messages are not concerned with rendering issues.
 * </p>
 */
public class StyledException extends GradleException {
    private final static Logger LOGGER = Logging.getLogger(StyledException.class);

    // Keep only the outermost style
    private final static Pattern STYLES_REGEX = Pattern.compile("<([A-Za-z]+)>(?:<([A-Za-z]+)>)*(.*?)(?:</\\2>)*</\\1>");
    private final String styledMessage;

    public StyledException() {
        super();
        styledMessage = "";
    }

    public StyledException(String message) {
        super(unstyled(message));
        styledMessage = message;
    }

    private static String unstyled(String message) {
        Matcher matcher = STYLES_REGEX.matcher(message);
        StringBuffer buf = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buf, "$3");
        }
        matcher.appendTail(buf);
        return buf.toString();
    }

    public StyledException(String message, @Nullable Throwable cause) {
        super(unstyled(message), cause);
        styledMessage = message;
    }

    public static String style(StyledTextOutput.Style style, String text) {
        if (text == null) {
            return null;
        }
        return "<" + style + ">" + text + "</" + style + ">";
    }

    public void render(StyledTextOutput output) {
        Matcher matcher = STYLES_REGEX.matcher(styledMessage);
        StringBuffer buf = new StringBuffer();
        while (matcher.find()) {
            buf.setLength(0);
            matcher.appendReplacement(buf, "");
            output.text(buf);
            String styleText = matcher.group(1);
            String txt = matcher.group(3);
            StyledTextOutput.Style style = toStyle(styleText);
            output.withStyle(style).text(txt);
        }
        buf.setLength(0);
        matcher.appendTail(buf);
        output.text(buf.toString());
    }

    private static StyledTextOutput.Style toStyle(String styleText) {
        try {
            return StyledTextOutput.Style.valueOf(StringUtils.capitalize(styleText));
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Style '{}' doesn't exist", styleText);
            return StyledTextOutput.Style.Normal;
        }
    }

}
