/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;

public class CompilerDaemonServer implements CompilerDaemonProtocol {
    private static final Logger LOGGER = Logging.getLogger(CompilerDaemonServer.class);

    @Override
    public <T extends CompileSpec> CompileResult execute(Compiler<T> compiler, T spec) {
        try {
            LOGGER.info("Executing {} in compiler daemon.", compiler);
            WorkResult result = compiler.execute(spec);
            LOGGER.info("Successfully executed {} in compiler daemon.", compiler);
            return new CompileResult(result.getDidWork(), null);
        } catch (Throwable t) {
            LOGGER.info("Exception executing {} in compiler daemon: {}.", compiler, t);
            return new CompileResult(true, t);
        }
    }
}
