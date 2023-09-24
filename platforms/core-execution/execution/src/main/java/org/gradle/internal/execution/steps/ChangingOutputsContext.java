/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution.steps;

/**
 * Context necessary for steps that change the outputs.
 *
 * This context doesn't add any new information, it encodes a requirement
 * in the type system that a step can change the outputs.
 */
public class ChangingOutputsContext extends InputChangesContext {
    public ChangingOutputsContext(InputChangesContext parent) {
        super(parent);
    }
}
