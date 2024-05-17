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

package org.gradle.integtests.tooling.fixture

import org.gradle.tooling.ToolingModelContract

@ToolingModelContract(subTypes = [ShallowChildModel.class, DeepChildModel.class])
interface BaseModel {
}

interface DeepChildModel extends BaseModel {
    String getDeepMessage()
}

interface ShallowChildModel extends BaseModel {
    String getShallowMessage()
}

interface SideModel extends BaseModel {
}

interface CompositeModel {
    BaseModel getNested()
}