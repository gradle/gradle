/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.findbugs;

import com.google.common.base.Objects;

import java.io.Serializable;
import java.util.List;

public class FindBugsSpec implements Serializable {
    private List<String> arguments;
    private String maxHeapSize;
    private boolean debugEnabled;

    public FindBugsSpec(List<String> arguments, String maxHeapSize, boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        this.maxHeapSize = maxHeapSize;
        this.arguments = arguments;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }
    
    public boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    public String toString() {
        return Objects.toStringHelper(this).add("arguments", arguments).add("debugEnabled", debugEnabled).toString();
    }
}
