/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.JavaVersion;

import java.io.PrintWriter;

/**
 * Mimics Throwable.printStackTrace(), but omits frames.
 */
public class ExceptionChainNoTraceRenderer {

    private static final String CAUSE_CAPTION = "Caused by: ";
    private static final String SUPPRESSED_CAPTION = "Suppressed: ";

    public static void render(Throwable throwable, String indent, PrintWriter writer) {
        innerRender(throwable, indent, "", writer);
    }

    public static void renderSuppressed(Throwable throwable, String indent, PrintWriter writer) {
        innerRender(throwable, indent, SUPPRESSED_CAPTION, writer);
    }

    public static void renderCausedBy(Throwable throwable, String indent, PrintWriter writer) {
        innerRender(throwable, indent, CAUSE_CAPTION, writer);
    }

    private static void innerRender(Throwable throwable, String indent, String prefix, PrintWriter writer) {
        writer.print(indent);
        writer.print(prefix);
        writer.println(throwable);
        if (JavaVersion.current().isJava7Compatible()) {
            //noinspection Since15
            for (Throwable suppressed : throwable.getSuppressed()) {
                renderSuppressed(suppressed, indent + "\t", writer);
            }
        }
        Throwable cause = throwable.getCause();
        if (cause != null) {
            renderCausedBy(cause, indent, writer);
        }
    }

}
