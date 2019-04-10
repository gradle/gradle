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

package org.gradle.api.internal.tasks.properties;

import org.gradle.internal.reflect.ParameterValidationContext;

import javax.annotation.Nullable;
import java.util.Collection;

public class DefaultParameterValidationContext implements ParameterValidationContext {
    private final Collection<String> messages;

    public DefaultParameterValidationContext(Collection<String> messages) {
        this.messages = messages;
    }

    private static String decorateMessage(@Nullable String ownerPath, String propertyName, String message) {
        String decoratedMessage;
        if (ownerPath == null) {
            decoratedMessage = "Property '" + propertyName + "' " + message + ".";
        } else {
            decoratedMessage = "Property '" + ownerPath + '.' + propertyName + "' " + message + ".";
        }
        return decoratedMessage;
    }

    @Override
    public void visitError(@Nullable String ownerPath, String propertyName, String message) {
        visitError(decorateMessage(ownerPath, propertyName, message));
    }

    @Override
    public void visitError(String message) {
        messages.add(message);
    }

    @Override
    public void visitErrorStrict(@Nullable String ownerPath, String propertyName, String message) {
        visitErrorStrict(decorateMessage(ownerPath, propertyName, message));
    }

    @Override
    public void visitErrorStrict(String message) {
        visitError(message);
    }
}
