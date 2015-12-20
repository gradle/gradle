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
import org.gradle.internal.exceptions.Contextual;
import org.gradle.model.internal.type.ModelType;

import java.util.Deque;

@Contextual
public class InvalidManagedModelElementTypeException extends RuntimeException {

    private static void createPathString(DefaultModelSchemaExtractionContext<?> extractionContext, StringBuilder out) {
        StringBuilder prefix = new StringBuilder("  ");

        Deque<String> descriptions = Lists.newLinkedList();
        DefaultModelSchemaExtractionContext<?> current = extractionContext;
        while (current != null) {
            descriptions.push(current.getDescription());
            current = current.getParent();
        }

        out.append(descriptions.pop());
        out.append('\n');

        while (!descriptions.isEmpty()) {
            out.append(prefix);
            out.append("\\--- ");
            out.append(descriptions.pop());

            if (!descriptions.isEmpty()) {
                out.append('\n');
                prefix.append("  ");
            }
        }
    }

    private static String getMessage(DefaultModelSchemaExtractionContext<?> extractionContext, String message) {
        ModelType<?> type = extractionContext.getType();
        StringBuilder out = new StringBuilder();
        out.append("Invalid managed model type ").append(type).append(": ").append(message);

        if (extractionContext.getParent() != null) {
            out.append('\n');
            out.append("The type was analyzed due to the following dependencies:\n");
            createPathString(extractionContext, out);
        }

        return out.toString();
    }

    private static String getMessage(DefaultModelSchemaExtractionContext<?> extractionContext) {
        StringBuilder out = new StringBuilder();
        out.append(extractionContext.getProblems().format());

        if (extractionContext.getParent() != null) {
            out.append("\n\n");
            out.append("The type was analyzed due to the following dependencies:\n");
            createPathString(extractionContext, out);
        }

        return out.toString();
    }

    public InvalidManagedModelElementTypeException(ModelSchemaExtractionContext<?> extractionContext) {
        super(getMessage((DefaultModelSchemaExtractionContext<?>) extractionContext), null);
    }

    public InvalidManagedModelElementTypeException(ModelSchemaExtractionContext<?> extractionContext, String message) {
        this(extractionContext, message, null);
    }

    public InvalidManagedModelElementTypeException(ModelSchemaExtractionContext<?> extractionContext, String message, Throwable throwable) {
        super(getMessage((DefaultModelSchemaExtractionContext<?>) extractionContext, message), throwable);
    }

}
