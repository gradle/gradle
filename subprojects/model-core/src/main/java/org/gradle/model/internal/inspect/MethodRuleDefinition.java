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

package org.gradle.model.internal.inspect;

import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.lang.annotation.Annotation;
import java.util.List;

public interface MethodRuleDefinition<R> {

    String getMethodName();

    <A extends Annotation> A getAnnotation(Class<A> annotationType);

    ModelType<R> getReturnType();

    List<ModelReference<?>> getReferences();

    ModelRuleDescriptor getDescriptor();

    ModelRuleInvoker<R> getRuleInvoker();
}
