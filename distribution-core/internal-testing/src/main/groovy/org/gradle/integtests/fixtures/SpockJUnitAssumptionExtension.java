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

package org.gradle.integtests.fixtures;

import org.junit.AssumptionViolatedException;
import org.opentest4j.TestAbortedException;
import org.spockframework.runtime.AbstractRunListener;
import org.spockframework.runtime.extension.AbstractGlobalExtension;
import org.spockframework.runtime.model.ErrorInfo;
import org.spockframework.runtime.model.SpecInfo;

public class SpockJUnitAssumptionExtension extends AbstractGlobalExtension {

    @Override
    public void visitSpec(SpecInfo spec) {
        spec.addListener(new AbstractRunListener() {
            @Override
            public void error(ErrorInfo error) {
                Throwable cause = error.getException();
                if (cause instanceof AssumptionViolatedException) {
                    throw new TestAbortedException(cause.getMessage(), cause);
                }
            }
        });
    }

}
