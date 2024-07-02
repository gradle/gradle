/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.actions;

import org.gradle.internal.cc.impl.fixtures.SomeToolingModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

import java.util.ArrayList;
import java.util.List;

public class FetchCustomModelForSameProjectInParallel implements BuildAction<List<SomeToolingModel>> {

    @Override
    public List<SomeToolingModel> execute(BuildController controller) {
        List<FetchModelForProject> actions = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            actions.add(new FetchModelForProject("nested-" + i));
        }
        return controller.run(actions);
    }

    private static class FetchModelForProject implements BuildAction<SomeToolingModel> {

        private final String id;

        private FetchModelForProject(String id) {
            this.id = id;
        }

        @Override
        public SomeToolingModel execute(BuildController controller) {
            System.out.println("Executing " + id);
            return controller.findModel(SomeToolingModel.class);
        }
    }
}
