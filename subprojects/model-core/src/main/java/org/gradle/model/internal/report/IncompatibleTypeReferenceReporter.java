/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.report;

import org.gradle.model.internal.core.ModelPromise;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class IncompatibleTypeReferenceReporter {

    private final static String INDENT = "  ";

    private final String creator;
    private final String path;
    private final String type;
    private final String description;
    private final boolean writable;
    private final Iterable<String> candidateTypes;

    public IncompatibleTypeReferenceReporter(String creator, String path, String type, String description, boolean writable, Iterable<String> candidateTypes) {
        this.creator = creator;
        this.path = path;
        this.type = type;
        this.description = description;
        this.writable = writable;
        this.candidateTypes = candidateTypes;
    }

    public static IncompatibleTypeReferenceReporter of(ModelRuleDescriptor creator, ModelPromise promise, ModelReference<?> reference, boolean writable) {
        return new IncompatibleTypeReferenceReporter(
                creator.toString(), reference.getPath().toString(), reference.getType().toString(), reference.getDescription(), writable,
                writable ? promise.getWritableTypeDescriptions() : promise.getReadableTypeDescriptions()
        );
    }

    public String asString() {
        StringWriter string = new StringWriter();
        writeTo(new PrintWriter(string));
        return string.toString();
    }

    public void writeTo(PrintWriter writer) {
        //"type-only model reference of type '%s'%s is ambiguous as multiple model elements are available for this type:%n  %s (created by %s)%n  %s (created by %s)",
        writer.print("Model reference to element '");
        writer.print(path);
        writer.print("' with type ");
        writer.print(type);
        if (description != null) {
            writer.print(" (");
            writer.print(description);
            writer.print(") ");
        }
        writer.println("is invalid due to incompatible types.");
        writer.print("This element was created by ");
        writer.print(creator);
        writer.print(" and can be ");
        writer.print(writable ? "mutated" : "read");
        writer.println(" as the following types:");
        boolean first = true;
        for (String candidateType : candidateTypes) {
            if (!first) {
                writer.println();
            }
            writer.print(INDENT);
            writer.print("- ");
            writer.print(candidateType);
            first = false;
        }
    }
}
