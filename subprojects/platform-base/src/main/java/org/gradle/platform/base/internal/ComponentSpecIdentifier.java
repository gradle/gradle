/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.internal;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

// TODO:RBO ComponentSpecIdentifier extends Named but what's the meaning of the name here? Is it also logically part of the identifier path?
// TODO:RBO How to arrange component identifiers in a hierarchy?
/**
 * An identifier for a {@link org.gradle.platform.base.ComponentSpec}, which has a name.
 */
@Incubating
public interface ComponentSpecIdentifier extends Named {

    // TODO:RBO Clarify what it means and what's possible to do with it.
    // TODO:RBO E.g. Can the return value always be used to resolve back to the identified component? If so, how?
    // TODO:RBO Wouldn't it be better to define a proper type for project/model paths?
    String getProjectPath();
}
