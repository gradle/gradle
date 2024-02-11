/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.transfer;

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;

import javax.annotation.Nullable;
import java.util.List;

/**
 * You should use {@link ExternalResource} instead of this type.
 */
public interface ExternalResourceLister {

    /**
     * Lists the direct children of the parent resource
     *
     * @param parent the resource to list from
     * @return A list of the direct children of the <code>parent</code>, null when the resource does not exist.
     */
    @Nullable
    List<String> list(ExternalResourceName parent) throws ResourceException;

}
