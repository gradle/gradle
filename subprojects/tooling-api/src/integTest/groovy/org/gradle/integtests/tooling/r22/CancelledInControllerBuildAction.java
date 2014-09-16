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

package org.gradle.integtests.tooling.r22;

import org.gradle.api.GradleException;
import org.gradle.integtests.tooling.r16.CustomModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

public class CancelledInControllerBuildAction implements BuildAction<Void> {
    public Void execute(BuildController controller) {
        System.out.println("waiting");
        controller.getModel(CustomModel.class);
        controller.getBuildModel();
        System.out.println("finished");
        throw new GradleException("Should be cancelled before the end of action.");
    }

}
