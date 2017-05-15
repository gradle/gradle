/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations.trace;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.OperationFinishEvent;

import java.util.Map;

class SerializedOperationFinish {

    final Object id;

    final long endTime;

    final Object result;
    final Class<?> resultType;

    final String failureMsg;

    SerializedOperationFinish(BuildOperationDescriptor descriptor, OperationFinishEvent finishEvent) {
        this.id = ((OperationIdentifier) descriptor.getId()).getId();
        this.endTime = finishEvent.getEndTime();
        this.result = finishEvent.getResult();
        this.resultType = result == null ? null : finishEvent.getResult().getClass();
        this.failureMsg = finishEvent.getFailure() == null ? null : finishEvent.getFailure().getMessage();
    }

    SerializedOperationFinish(Map<String, ?> map) throws ClassNotFoundException {
        this.id = map.get("id");
        this.endTime = (Long) map.get("endTime");
        this.result = map.get("result");

        Object resultTypeString = map.get("resultType");
        if (resultTypeString != null) {
            this.resultType = getClass().getClassLoader().loadClass(resultTypeString.toString());
        } else {
            this.resultType = null;
        }

        this.failureMsg = (String) map.get("failure");
    }

    Map<String, ?> toMap() {
        ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();

        // Order is optimised for humans looking at the log.

        map.put("id", id);

        if (result != null) {
            map.put("result", result);
            map.put("resultType", resultType.getName());
        }

        if (failureMsg != null) {
            map.put("failure", failureMsg);
        }

        map.put("endTime", endTime);

        return map.build();
    }

}
