/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.base.internal.tasks.apigen.abi;

import com.google.common.collect.ComparisonChain;

public class ParameterAnnotationSig extends AnnotationSig {
    private final int parameter;

    public ParameterAnnotationSig(String name, boolean visible, int parameter) {
        super(name, visible);
        this.parameter = parameter;
    }

    public int getParameter() {
        return parameter;
    }

    @Override
    public int compareTo(AnnotationSig o) {
        return ComparisonChain.start()
            .compare(parameter, ((ParameterAnnotationSig) o).parameter)
            .compare(getName(), o.getName())
            .compare(isVisible(), o.isVisible())
            .result();
    }
}
