/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal;

public class CrashingWorker implements TestProtocol {
    @Override
    public Object convert(String param1, long param2) {
        if (param1.equals("halt")) {
            Runtime.getRuntime().halt((int) param2);
        } else {
            System.exit((int) param2);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public void doSomething() {
        throw new UnsupportedOperationException();
    }
}
