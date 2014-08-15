/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativebinaries.toolchain.internal.tools;

import org.gradle.api.Action;
import org.gradle.internal.Actions;

import java.util.ArrayList;
import java.util.List;

public class DefaultCommandLineToolConfiguration implements CommandLineToolConfigurationInternal {
    private List<Action<? super List<String>>> argActions = new ArrayList<Action<? super List<String>>>();
    private String name;

    public DefaultCommandLineToolConfiguration(String name) {
        this.name = name;
    }

    public void withArguments(Action<? super List<String>>  action) {
        argActions.add(action);
    }

    public Action<List<String>> getArgAction() {
        return Actions.composite(argActions);
    }

    public String getName() {
        return this.name;
    }
}
