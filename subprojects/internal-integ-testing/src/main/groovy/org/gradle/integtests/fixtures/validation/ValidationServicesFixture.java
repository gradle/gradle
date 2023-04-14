/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.integtests.fixtures.validation;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ExecutionGlobalServices;

public class ValidationServicesFixture {

    public static ServiceRegistry getServices() {
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        registry.addProvider(new Object() {
            ExecutionGlobalServices.AnnotationHandlerRegistration createAnnotationRegistration() {
                return () -> ImmutableList.of(ValidationProblem.class);
            }

            PropertyAnnotationHandler createValidationProblemAnnotationHandler() {
                return new ValidationProblemPropertyAnnotationHandler();
            }
        });
        return registry;
    }

}
