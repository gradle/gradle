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

package org.gradle.architecture.test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

public class FreezeInstructionsPrintingArchRule implements ArchRule {
    private final ArchRule delegate;

    public FreezeInstructionsPrintingArchRule(ArchRule delegate) {
        this.delegate = delegate;
    }

    @Override
    public void check(JavaClasses classes) {
        Assertions.check(this, classes);
    }

    @Override
    public ArchRule because(String reason) {
        return new FreezeInstructionsPrintingArchRule(delegate.because(reason));
    }

    @Override
    public ArchRule as(String newDescription) {
        return new FreezeInstructionsPrintingArchRule(delegate.as(newDescription));
    }

    @Override
    public EvaluationResult evaluate(JavaClasses classes) {
        try {
            return delegate.evaluate(classes);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("freeze.store")) {
                throw new RuntimeException("ArchUnit violations changed, please refreeze and check the differences by running ./gradlew architecture-test:test -ParchunitRefreeze", e);
            } else {
                throw e;
            }
        }
    }

    @Override
    public ArchRule allowEmptyShould(boolean allowEmptyShould) {
        return delegate.allowEmptyShould(allowEmptyShould);
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }
}
