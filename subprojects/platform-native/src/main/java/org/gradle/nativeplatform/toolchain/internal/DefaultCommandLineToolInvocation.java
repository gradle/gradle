/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.internal.operations.logging.BuildOperationLogger;

import java.io.File;
import java.util.List;
import java.util.Map;

class DefaultCommandLineToolInvocation implements CommandLineToolInvocation {
    private String description;
    private final File workDirectory;
    private final Iterable<String> args;
    private final CommandLineToolContext context;
    private final BuildOperationLogger oplogger;

    DefaultCommandLineToolInvocation(String description, File workDirectory, Iterable<String> args, CommandLineToolContext context, BuildOperationLogger oplogger) {
        this.description = description;
        this.workDirectory = workDirectory;
        this.args = args;
        this.context = context;
        this.oplogger = oplogger;
    }

    @Override
    public Iterable<String> getArgs() {
        return args;
    }

    @Override
    public BuildOperationLogger getLogger() {
        return oplogger;
    }

    @Override
    public File getWorkDirectory() {
        return workDirectory;
    }

    @Override
    public List<File> getPath() {
        return context.getPath();
    }

    @Override
    public Map<String, String> getEnvironment() {
        return context.getEnvironment();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
