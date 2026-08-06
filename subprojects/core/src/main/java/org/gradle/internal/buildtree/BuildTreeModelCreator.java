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

package org.gradle.internal.buildtree;

/**
 * Responsible for creating a model from the build tree model.
 * <p>
 * Each phase returns a {@link BuildTreeModelCreatorResult} carrying the client model together with any failures that
 * resilient model building held behind partial results. Those failures must still fail the build, even though partial
 * models were returned to the client.
 */
public interface BuildTreeModelCreator {
    BuildTreeModelCreatorResult<Void> beforeTasks(BuildTreeModelAction<?> action);

    <T> BuildTreeModelCreatorResult<T> fromBuildModel(BuildTreeModelAction<? extends T> action);
}
