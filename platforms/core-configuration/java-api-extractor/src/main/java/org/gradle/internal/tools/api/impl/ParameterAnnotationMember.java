/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.tools.api.impl;

public class ParameterAnnotationMember extends AnnotationMember {

    private final int parameter;

    public ParameterAnnotationMember(String name, boolean visible, int parameter) {
        super(name, visible);
        this.parameter = parameter;
    }

    public int getParameter() {
        return parameter;
    }

    @Override
    protected int kindRank() {
        return 1;
    }

    @Override
    public int compareTo(AnnotationMember o) {
        if (!(o instanceof ParameterAnnotationMember)) {
            // The rank of the kinds decides, and it never leaves them equal
            return super.compare(o).result();
        }
        return super.compare(o)
            .compare(parameter, ((ParameterAnnotationMember) o).parameter)
            .result();
    }
}
