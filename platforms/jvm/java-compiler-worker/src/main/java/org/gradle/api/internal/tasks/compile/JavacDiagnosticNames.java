/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.sun.tools.javac.util.JavacMessages;
import org.jspecify.annotations.Nullable;

import javax.tools.Diagnostic;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derives problem names for javac diagnostics.
 * <p>
 * A problem name describes the kind of problem without the details of one occurrence. javac's own kinds are its
 * diagnostic codes, keys into its message bundle; the message template behind a key is the sentence for the kind, with
 * placeholders where the occurrence's details go. The name is the first line of that template with the placeholders
 * collapsed and the first letter capitalized, for example {@code Cannot find symbol} or
 * {@code ... is already defined in ...}. The template is read for
 * {@link Locale#ROOT}, so the name does not depend on the JVM locale; the localized message stays in the contextual
 * label and the details.
 * <p>
 * Messages of annotation processors arrive under the {@code proc.messager} codes with the processor's text as the
 * only argument, so their name is fixed per severity. Codes that resolve to no template, such as those of compiler
 * plugins that bring their own keys without a bundle, are used as the name unchanged, as is every code when the
 * compiler's messages are not available.
 */
class JavacDiagnosticNames {

    static final String PLACEHOLDER_SUBSTITUTE = "...";

    private static final String PROCESSOR_MESSAGE_CODE_SUFFIX = ".proc.messager";
    private static final String MISSING_TEMPLATE_PREFIX = "compiler message file broken";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+(?:,[^{}]*)?}");
    private static final Pattern SUBSTITUTE_RUN = Pattern.compile(Pattern.quote(PLACEHOLDER_SUBSTITUTE) + "(?:[\\s,;:/-]*" + Pattern.quote(PLACEHOLDER_SUBSTITUTE) + ")+");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private JavacDiagnosticNames() {
    }

    static String nameFor(@Nullable String code, Diagnostic.Kind kind, @Nullable JavacMessages messages) {
        if (code == null) {
            return bySeverity("Java compiler", kind);
        }
        if (code.endsWith(PROCESSOR_MESSAGE_CODE_SUFFIX)) {
            return bySeverity("Annotation processor", kind);
        }
        if (messages == null) {
            return code;
        }
        String template = messages.getLocalizedString(Locale.ROOT, code);
        if (template.startsWith(MISSING_TEMPLATE_PREFIX)) {
            return code;
        }
        String name = template.split("\\R", 2)[0];
        name = PLACEHOLDER.matcher(name).replaceAll(PLACEHOLDER_SUBSTITUTE);
        name = SUBSTITUTE_RUN.matcher(name).replaceAll(PLACEHOLDER_SUBSTITUTE);
        name = CONTROL_CHARACTERS.matcher(name).replaceAll("");
        name = WHITESPACE.matcher(name).replaceAll(" ").trim();
        if (name.isEmpty() || name.equals(PLACEHOLDER_SUBSTITUTE)) {
            return code;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String bySeverity(String producer, Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return producer + " error";
            case WARNING:
            case MANDATORY_WARNING:
                return producer + " warning";
            default:
                return producer + " note";
        }
    }
}
