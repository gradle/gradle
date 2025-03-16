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

import org.gradle.util.internal.TreeVisitor;

public abstract class ExceptionContextVisitor extends TreeVisitor<Throwable> {
    protected abstract void visitCause(Throwable cause);

    protected abstract void visitLocation(String location);

    /**
     * Should be called after each time this visitor has finished visiting.
     *
     * This method can be used to perform any cleanup or final processing related
     * to the visitor's purpose.
     */
    protected void endVisiting() {
        // default is no-op
    }
}
