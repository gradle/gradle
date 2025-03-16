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
package org.gradle.util.internal;

import groovy.lang.Closure;

public class AlwaysTrue extends Closure<Boolean> {
    public AlwaysTrue() {
        super(null);
    }

    public AlwaysTrue(Object owner, Object thisObject) {
        super(owner, thisObject);
    }

    public AlwaysTrue(Object owner) {
        super(owner);
    }

    @Override
    public Boolean call() {
        return true;
    }

    @Override
    public Boolean call(Object... args) {
        return true;
    }

    @Override
    public Boolean call(Object arguments) {
        return true;
    }

    public Boolean doCall() {
        return true;
    }
}
