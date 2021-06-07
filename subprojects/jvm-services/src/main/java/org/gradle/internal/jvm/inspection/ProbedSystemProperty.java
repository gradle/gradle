/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

enum ProbedSystemProperty {

    JAVA_HOME("java.home"),
    VERSION("java.version"),
    VENDOR("java.vendor"),
    ARCH("os.arch"),
    VM("java.vm.name"),
    VM_VERSION("java.vm.version"),
    RUNTIME("java.runtime.name"),
    RUNTIME_VERSION("java.runtime.version"),
    Z_ERROR("Internal"); // This line MUST be last!

    private final String key;

    ProbedSystemProperty(String key) {
        this.key = key;
    }

    String getSystemPropertyKey() {
        return key;
    }

}
