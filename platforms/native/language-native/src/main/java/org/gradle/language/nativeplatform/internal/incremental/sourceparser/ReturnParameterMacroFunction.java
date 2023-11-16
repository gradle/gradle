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

import org.gradle.language.nativeplatform.internal.Expression;

import java.util.List;

/**
 * A macro function that returns the value of one of its parameters.
 */
public class ReturnParameterMacroFunction extends AbstractMacroFunction {
    private final int parameterToReturn;

    public ReturnParameterMacroFunction(String name, int parameters, int parameterToReturn) {
        super(name, parameters);
        this.parameterToReturn = parameterToReturn;
    }

    public int getParameterToReturn() {
        return parameterToReturn;
    }

    @Override
    protected String getBody() {
        return "=> return parameter " + parameterToReturn;
    }

    @Override
    public Expression evaluate(List<Expression> arguments) {
        return arguments.get(parameterToReturn).asMacroExpansion();
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        ReturnParameterMacroFunction other = (ReturnParameterMacroFunction) obj;
        return parameterToReturn == other.parameterToReturn;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ parameterToReturn * 31;
    }
}
