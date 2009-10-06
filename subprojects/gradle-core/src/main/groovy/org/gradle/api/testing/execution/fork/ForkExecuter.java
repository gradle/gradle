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
package org.gradle.api.testing.execution.fork;

import java.util.List;

/**
 * Interface with methods expected to be supported by the controller that is instanciated by the ForkLaunchMain.
 *
 * @author Tom Eyckmans
 */
public interface ForkExecuter {
    void setSharedClassLoader(ClassLoader sharedClassLoader);

    void setControlClassLoader(ClassLoader controlClassLoader);

    void setSandboxClassLoader(ClassLoader sandboxClassLoader);

    void setArguments(List<String> arguments);

    void execute();
}
