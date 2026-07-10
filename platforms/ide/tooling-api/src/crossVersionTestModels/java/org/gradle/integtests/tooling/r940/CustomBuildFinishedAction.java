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

import org.gradle.integtests.tooling.r48.CustomBuildFinishedModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.FetchModelResult;

import java.util.function.Supplier;

class CustomBuildFinishedAction implements BuildAction<String> {

    public enum CallType {
        GET_MODEL,
        FIND_MODEL,
        FETCH
    }

    static final String FAILURE_RESULT = "<failure>";

    final CustomBuildFinishedAction.CallType callType;

    CustomBuildFinishedAction(CustomBuildFinishedAction.CallType callType) {
        this.callType = callType;
    }

    @Override
    public String execute(BuildController controller) {
        System.out.println("Running CustomBuildFinishedAction");
        if (callType == CustomBuildFinishedAction.CallType.GET_MODEL) {
            return tryGet(() -> controller.getModel(CustomBuildFinishedModel.class).toString());
        } else if (callType == CustomBuildFinishedAction.CallType.FIND_MODEL) {
            return tryGet(() -> controller.findModel(CustomBuildFinishedModel.class).toString());
        } else if (callType == CustomBuildFinishedAction.CallType.FETCH) {
            FetchModelResult<CustomBuildFinishedModel> result = controller.fetch(CustomBuildFinishedModel.class);
            if (result.getModel() != null) {
                assert result.getFailures().isEmpty();
                return result.getModel().getValue();
            } else {
                assert !result.getFailures().isEmpty();
                return FAILURE_RESULT;
            }
        } else {
            throw new UnsupportedOperationException("Unknown callType: $callType");
        }
    }

    private static String tryGet(Supplier<String> tryGet) {
        try {
            return tryGet.get();
        } catch (Exception ignored) {
            return FAILURE_RESULT;
        }
    }
}
