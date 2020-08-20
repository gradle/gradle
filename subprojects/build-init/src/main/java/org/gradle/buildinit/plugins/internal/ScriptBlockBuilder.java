/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.Action;

import javax.annotation.Nullable;

public interface ScriptBlockBuilder {
    /**
     * Adds a property assignment statement to this block
     */
    void propertyAssignment(@Nullable String comment, String propertyName, Object propertyValue, boolean legacyProperty);

    /**
     * Adds a method invocation statement to this block
     */
    void methodInvocation(@Nullable String comment, String methodName, Object... methodArgs);

    /**
     * Adds a method invocation statement to this block
     */
    void methodInvocation(@Nullable String comment, BuildScriptBuilder.Expression target, String methodName, Object... methodArgs);

    /**
     * Adds a block statement to this block.
     *
     * @return The body of the block, to which further statements can be added.
     */
    ScriptBlockBuilder block(@Nullable String comment, String methodName);

    /**
     * Adds a block statement to this block.
     */
    void block(@Nullable String comment, String methodName, Action<? super ScriptBlockBuilder> blockContentsBuilder);

    /**
     * Adds an element to the given container.
     *
     * @return an expression that can be used to refer to the element. Note: currently this expression can only be used within this current block.
     */
    BuildScriptBuilder.Expression containerElement(@Nullable String comment, String container, String elementName, @Nullable String elementType, Action<? super ScriptBlockBuilder> blockContentsBuilder);

    /**
     * Returns a property expression that can be used as a method argument or property assignment value
     */
    BuildScriptBuilder.Expression propertyExpression(String value);
}
