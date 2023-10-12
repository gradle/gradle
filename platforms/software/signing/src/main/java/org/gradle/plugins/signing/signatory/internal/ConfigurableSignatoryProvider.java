/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.plugins.signing.signatory.internal;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.signatory.SignatoryProvider;
import org.gradle.security.internal.BaseSignatoryProvider;
import org.gradle.util.internal.ConfigureUtil;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.asType;

public interface ConfigurableSignatoryProvider<T extends Signatory> extends BaseSignatoryProvider<T>, SignatoryProvider<T> {
    @Override
    default void configure(SigningExtension settings, Closure closure) {
        ConfigureUtil.configure(closure, new Object() {
            @SuppressWarnings("unused") // invoked by Groovy
            public void methodMissing(String name, Object args) {
                createSignatoryFor(settings.getProject(), name, asType(args, Object[].class));
            }
        });
    }

    void createSignatoryFor(Project project, String name, Object[] args);
}
