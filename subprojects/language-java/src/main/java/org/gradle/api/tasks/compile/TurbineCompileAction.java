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

package org.gradle.api.tasks.compile;

import org.gradle.workers.WorkAction;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class TurbineCompileAction implements WorkAction<TurbineWorkParameters> {
    @Override
    public void execute() {
        TurbineWorkParameters parameters = getParameters();

        List<String> options = new ArrayList<>();
        options.add("--javacopts");
        options.add("--release"); options.add("8");
        options.add("--");
        options.add("--classpath");
        options.addAll(parameters.getClasspath().getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toList()));

        options.add("--output");
        options.add(parameters.getOutput().get().getAsFile().getAbsolutePath());

        options.add("--sources");
        options.addAll(parameters.getSources().getAsFileTree().getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toList()));
        try {
            Class<?> turbineMain = Thread.currentThread().getContextClassLoader().loadClass("com.google.turbine.main.Main");
            Method main = turbineMain.getMethod("compile", String[].class);
            main.invoke(null, (Object) options.toArray(new String[0]));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
