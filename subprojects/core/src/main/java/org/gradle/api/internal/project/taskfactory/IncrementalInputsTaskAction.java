/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Task;
import org.gradle.api.execution.incremental.IncrementalInputs;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.internal.Describables;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.InputFileChanges;
import org.gradle.internal.execution.history.impl.DefaultIncrementalInputs;
import org.gradle.internal.execution.history.impl.RebuildIncrementalInputs;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.reflect.JavaMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class IncrementalInputsTaskAction extends AbstractIncrementalTaskAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncrementalInputsTaskAction.class);

    public IncrementalInputsTaskAction(Class<? extends Task> type, Method method) {
        super(type, method);
    }

    protected void doExecute(final Task task, String methodName) {
        final Map<Object, String> propertyNameByValue = new HashMap<Object, String>();
        for (InputFilePropertySpec inputFileProperty : getContext().getTaskProperties().getInputFileProperties()) {
            Object value = inputFileProperty.getValue().call();
            if (value != null) {
                propertyNameByValue.put(value, inputFileProperty.getPropertyName());
            }
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent") final ExecutionStateChanges changes = getContext().getExecutionStateChanges().get();
        IncrementalInputs incrementalTaskInputs = changes.visitInputFileChanges(new ExecutionStateChanges.IncrementalInputsVisitor<IncrementalInputs>() {
            @Override
            public IncrementalInputs visitRebuild(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> allFileInputs) {
                return new RebuildIncrementalInputs(allFileInputs, propertyNameByValue, Describables.of(task));
            }

            @Override
            public IncrementalInputs visitIncrementalChange(InputFileChanges inputFileChanges) {
                return new DefaultIncrementalInputs(inputFileChanges, propertyNameByValue);
            }
        });

        getContext().setTaskExecutedIncrementally(incrementalTaskInputs.isIncremental());
        JavaMethod.of(task, Object.class, methodName, IncrementalInputs.class).invoke(task, incrementalTaskInputs);
    }
}
