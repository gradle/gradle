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
package org.gradle.nativeplatform.internal;

import org.gradle.nativeplatform.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A tool that is part of a tool chain (compiler, linker, assembler, etc).
 */
public class DefaultTool implements Tool {
    private final ArrayList<String> args = new ArrayList<String>();

    @Override
    public List<String> getArgs() {
        return args;
    }

    @Override
    public void args(String... args) {
        Collections.addAll(this.args, args);
    }
}
