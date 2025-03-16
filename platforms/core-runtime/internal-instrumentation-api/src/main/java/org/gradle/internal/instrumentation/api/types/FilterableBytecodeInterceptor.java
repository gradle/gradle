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

package org.gradle.internal.instrumentation.api.types;

/**
 * A base interface for all bytecode interceptors, that can be filtered by {@link BytecodeInterceptorFilter}.
 */
public interface FilterableBytecodeInterceptor {

    BytecodeInterceptorType getType();

    /**
     * A marker interface that indicates that a class is used for bytecode upgrades.
     */
    interface BytecodeUpgradeInterceptor extends FilterableBytecodeInterceptor {
        @Override
        default BytecodeInterceptorType getType() {
            return BytecodeInterceptorType.BYTECODE_UPGRADE;
        }
    }

    /**
     * A marker interface that indicates that a class is used for configuration cache instrumentation.
     */
    interface InstrumentationInterceptor extends FilterableBytecodeInterceptor {
        @Override
        default BytecodeInterceptorType getType() {
            return BytecodeInterceptorType.INSTRUMENTATION;
        }
    }

    interface BytecodeUpgradeReportInterceptor extends FilterableBytecodeInterceptor {
        @Override
        default BytecodeInterceptorType getType() {
            return BytecodeInterceptorType.BYTECODE_UPGRADE_REPORT;
        }
    }
}
