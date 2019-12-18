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

package org.gradle.integtests.fixtures

import org.junit.runners.model.Statement

class ExpectingFailureRuleStatement extends Statement {
    private final Statement next
    private final String failsBecause

    ExpectingFailureRuleStatement(String failsBecause, Statement next) {
        this.failsBecause = failsBecause
        this.next = next
    }

    @Override
    void evaluate() throws Throwable {
        try {
            next.evaluate()
            throw new CatchFeatureFailuresRunListener.UnexpectedSuccessException()
        } catch (CatchFeatureFailuresRunListener.UnexpectedSuccessException ex) {
            throw ex
        } catch (Throwable ex) {
            System.err.println("Failed with ${failsBecause} as expected:")
            ex.printStackTrace()
        }
    }
}
