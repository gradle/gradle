/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import org.gradle.language.nativeplatform.internal.MacroFunction;

public abstract class AbstractMacroFunction implements MacroFunction {
    private final String name;
    private final int parameters;

    AbstractMacroFunction(String name, int parameters) {
        this.name = name;
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "#define " + getName() + "(...) " + getBody();
    }

    protected String getBody() {
        return "=> ???";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getParameterCount() {
        return parameters;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        AbstractMacroFunction other = (AbstractMacroFunction) obj;
        return name.equals(other.name) && parameters == other.parameters;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
