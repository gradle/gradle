/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;

public interface CopySpecInternal extends CopySpec {

    //TODO - does this belong here or on the resolver? PEZ
    boolean hasSource();

    Iterable<CopySpecInternal> getChildren();

    CopySpecInternal addChild();

    CopySpecInternal addChildBeforeSpec(CopySpecInternal childSpec);

    CopySpecInternal addFirst();

    void walk(Action<? super CopySpecResolver> action);

    CopySpecResolver buildRootResolver();

    CopySpecResolver buildResolverRelativeToParent(CopySpecResolver parent);

}
