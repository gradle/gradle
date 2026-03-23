/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r940;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.Serializable;

// Build action that fetches GradleBuild to initialize, then queries DummyModel and returns its classloader
class DummyModelAction implements BuildAction<String>, Serializable {
    @Override
    public String execute(BuildController controller) {
        // Fetch GradleBuild to force init script evaluation
        controller.fetch(GradleBuild.class);

        // Fetch DummyModel
        FetchModelResult<DummyModel> result = controller.fetch(DummyModel.class);
        DummyModel dummyModel = result.getModel();
        assert dummyModel != null;
        Object unpacked = new ProtocolToModelAdapter().unpack(dummyModel);
        ClassLoader modelBuildersClassLoader = unpacked.getClass().getClassLoader();
        return modelBuildersClassLoader.toString();
    }
}
