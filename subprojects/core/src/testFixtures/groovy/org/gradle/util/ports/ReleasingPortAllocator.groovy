/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.util.ports

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A TestRule that releases any assigned ports after the test executes.
 */
class ReleasingPortAllocator implements PortAllocator, TestRule {
    final PortAllocator delegate
    List<Integer> portsAllocated = new ArrayList<>()

    ReleasingPortAllocator() {
        this(FixedAvailablePortAllocator.getInstance())
    }

    ReleasingPortAllocator(PortAllocator delegate) {
        this.delegate = delegate
    }

    @Override
    int assignPort() {
        int port = delegate.assignPort()
        portsAllocated.add(port)
        return port
    }

    @Override
    void releasePort(int port) {
        delegate.releasePort(port)
        portsAllocated.removeAll { it == port }
    }

    @Override
    Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            void evaluate() throws Throwable {
                base.evaluate()
                for (int port : portsAllocated) {
                    delegate.releasePort(port)
                }
                portsAllocated.clear()
            }
        }
    }
}
