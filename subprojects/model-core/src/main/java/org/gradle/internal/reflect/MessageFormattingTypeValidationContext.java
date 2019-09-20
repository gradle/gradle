/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.reflect;

import javax.annotation.Nullable;

abstract public class MessageFormattingTypeValidationContext implements TypeValidationContext {
    private final Class<?> rootType;

    public MessageFormattingTypeValidationContext(Class<?> rootType) {
        this.rootType = rootType;
    }

    @Override
    public void visitProblem(Severity severity, @Nullable String parentProperty, @Nullable String property, String message) {
        StringBuilder builder = new StringBuilder();
        builder.append("Type '");
        builder.append(rootType.getTypeName());
        builder.append("': ");
        if (property != null) {
            builder.append("property '");
            if (parentProperty != null) {
                builder.append(parentProperty);
                builder.append('.');
            }
            builder.append(property);
            builder.append("' ");
        }
        builder.append(message);
        builder.append('.');
        recordProblem(severity, builder.toString());
    }

    abstract protected void recordProblem(Severity severity, String message);
}
