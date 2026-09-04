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

package org.gradle.integtests.tooling.r930;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

class FetchUnknownModelAction implements BuildAction<FetchUnknownModelAction.Result> {
    @Override
    public Result execute(BuildController controller) {
        FetchModelResult<UnknownModel> result = controller.fetch(null, UnknownModel.class, null, null);
        assert result.getModel() == null;
        assert result.getFailures().size() == 1;
        Failure failure = result.getFailures().iterator().next();
        List<String> causes = failure.getCauses().stream()
            .map(Failure::getMessage)
            .collect(Collectors.toList());
        return new Result(causes, failure.getDescription());
    }

    public static class Result implements Serializable {
        public final List<String> causes;
        public final String failureDescription;

        public Result(List<String> causes, String failureDescription) {
            this.causes = causes;
            this.failureDescription = failureDescription;
        }
    }
}
