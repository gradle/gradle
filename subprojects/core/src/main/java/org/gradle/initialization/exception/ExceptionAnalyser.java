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
package org.gradle.initialization.exception;

public interface ExceptionAnalyser {
    /**
     * Transforms the given build failure to add context where relevant and to remove unnecessary noise.
     *
     * <p>Note that the argument may be mutated as part of the transformation, for example its causes or stack trace may be changed.</p>
     */
    RuntimeException transform(Throwable exception);
}
