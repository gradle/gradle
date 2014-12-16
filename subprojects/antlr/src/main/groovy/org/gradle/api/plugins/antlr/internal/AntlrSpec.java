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

package org.gradle.api.plugins.antlr.internal;

import java.io.Serializable;

import java.util.List;

public class AntlrSpec implements Serializable {

    private List<String> arguments;
    private String maxHeapSize;

    public AntlrSpec(List<String> arguments, String maxHeapSize) {
        this.arguments = arguments;
        this.maxHeapSize = maxHeapSize;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }
}
