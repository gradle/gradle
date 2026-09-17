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

package org.gradle.integtests.tooling.r980;

import org.gradle.integtests.tooling.r16.CustomModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FetchFailureDescriptionsAction implements BuildAction<List<FetchFailureDescriptionsAction.FailureNode>> {

    @Override
    public List<FailureNode> execute(BuildController controller) {
        FetchModelResult<CustomModel> result = controller.fetch(CustomModel.class);
        List<FailureNode> failures = new ArrayList<FailureNode>();
        for (Failure failure : result.getFailures()) {
            failures.add(snapshot(failure));
        }
        return failures;
    }

    private static FailureNode snapshot(Failure failure) {
        List<FailureNode> causes = new ArrayList<FailureNode>();
        for (Failure cause : failure.getCauses()) {
            causes.add(snapshot(cause));
        }
        return new FailureNode(failure.getMessage(), failure.getDescription(), failure.getOwnDescription(), causes);
    }

    public static class FailureNode implements Serializable {
        public final String message;
        public final String description;
        public final String ownDescription;
        public final List<FailureNode> causes;

        public FailureNode(String message, String description, String ownDescription, List<FailureNode> causes) {
            this.message = message;
            this.description = description;
            this.ownDescription = ownDescription;
            this.causes = causes;
        }
    }
}
