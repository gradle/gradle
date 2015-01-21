/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.Lists;
import org.gradle.model.internal.type.ModelType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Deque;

public class InvalidManagedModelElementTypeException extends RuntimeException {

    private static String createPathString(ModelSchemaExtractionContext<?> extractionContext) {
        StringBuilder prefix = new StringBuilder("  ");
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        Deque<String> descriptions = Lists.newLinkedList();
        ModelSchemaExtractionContext<?> current = extractionContext;
        while (current != null) {
            descriptions.push(current.getDescription());
            current = current.getParent();
        }

        writer.println(descriptions.pop());

        while (!descriptions.isEmpty()) {
            writer.print(prefix);
            writer.print("\\--- ");
            writer.print(descriptions.pop());

            if (!descriptions.isEmpty()) {
                writer.println();
                prefix.append("  ");
            }
        }

        return out.toString();
    }

    private static String getMessage(ModelSchemaExtractionContext<?> extractionContext, String message) {
        ModelType<?> type = extractionContext.getType();
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        writer.print("Invalid managed model type " + type + ": " + message);

        if (extractionContext.getParent() != null) {
            writer.println();
            writer.println("The type was analyzed due to the following dependencies:");
            writer.print(createPathString(extractionContext));
        }

        return out.toString();
    }

    public InvalidManagedModelElementTypeException(ModelSchemaExtractionContext<?> extractionContext, String message) {
        this(extractionContext, message, null);
    }

    public InvalidManagedModelElementTypeException(ModelSchemaExtractionContext<?> extractionContext, String message, Throwable throwable) {
        super(getMessage(extractionContext, message), throwable);
    }

}
