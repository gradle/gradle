/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.integtests.tooling.r930;

import java.io.Serializable;
import java.util.List;

public class Result<T> implements Serializable {
    T modelValue;
    List<String> failureMessages;
    List<String> causes;

    public Result(T modelValue, List<String> failureMessages, List<String> causes) {
        this.modelValue = modelValue;
        this.failureMessages = failureMessages;
        this.causes = causes;
    }
}
