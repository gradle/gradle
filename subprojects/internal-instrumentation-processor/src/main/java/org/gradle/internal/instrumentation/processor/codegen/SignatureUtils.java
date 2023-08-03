/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.processor.codegen;

import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.ParameterKindInfo;

public class SignatureUtils {
    public static boolean hasCallerClassName(CallableInfo callableInfo) {
        return callableInfo.getParameters().stream().anyMatch(it -> it.getKind() == ParameterKindInfo.CALLER_CLASS_NAME);
    }
}
