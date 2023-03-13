/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.processor.codegen.groovy;

import org.objectweb.asm.Type;

import java.util.Objects;

class ParameterMatchEntry {
    final Type type;
    final Kind kind;

    ParameterMatchEntry(Type type, Kind kind) {
        this.type = type;
        this.kind = kind;
    }

    enum Kind {
        RECEIVER_AS_CLASS, RECEIVER, PARAMETER, VARARG
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParameterMatchEntry)) {
            return false;
        }
        ParameterMatchEntry that = (ParameterMatchEntry) o;
        return Objects.equals(type, that.type) && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, kind);
    }
}
