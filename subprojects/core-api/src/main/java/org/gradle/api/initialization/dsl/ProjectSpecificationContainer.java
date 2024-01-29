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

package org.gradle.api.initialization.dsl;

import com.h0tk3y.kotlin.staticObjectNotation.Adding;
import com.h0tk3y.kotlin.staticObjectNotation.Restricted;
import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;

@Restricted
public interface ProjectSpecificationContainer {
    // Can't currently have both subproject methods annotated with @Adding
//    @Adding
    ProjectSpecification subproject(String logicalPath);

    @Adding
    ProjectSpecification subproject(String logicalPath, Action<? super ProjectSpecification> action);

    // this is not right, but Property<String> isn't supported, @Restricted requires this to be a property,
    // and @Configuring requires an Action argument
    @Adding
    void setProjectDirRelativePath(String projectRelativePath);

    String getLogicalPath();

    DirectoryProperty getProjectDir();
}
