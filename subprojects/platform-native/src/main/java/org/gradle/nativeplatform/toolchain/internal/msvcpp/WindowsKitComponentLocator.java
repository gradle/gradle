/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import java.io.File;
import java.util.List;

import org.gradle.platform.base.internal.toolchain.ToolSearchResult;

public interface WindowsKitComponentLocator<T extends WindowsKitComponent> {
    String[] PLATFORMS = new String[] {"x86", "x64"};

    SearchResult<T> locateComponents(File candidate);

    List<T> locateAllComponents();

    interface SearchResult<T> extends ToolSearchResult {
        T getComponent();
    }
}
