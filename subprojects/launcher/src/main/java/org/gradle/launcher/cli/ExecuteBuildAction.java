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
package org.gradle.launcher.cli;

import org.gradle.StartParameter;
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;

import java.io.Serializable;

public class ExecuteBuildAction implements BuildAction<Void>, Serializable {
    private final StartParameter startParameter;

    public ExecuteBuildAction(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    public Void run(BuildController buildController) {
        buildController.setStartParameter(startParameter);
        buildController.run();
        return null;
    }
}
