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

package org.gradle.api.tasks.gosu;

import org.gradle.api.tasks.compile.BaseForkOptions;

/**
 * Fork options for Gosu compilation. Only take effect if {@code GosuCompileOptions.fork}
 * is {@code true}.
 */
public class GosuForkOptions extends BaseForkOptions {
    private static final long serialVersionUID = 0;
}
