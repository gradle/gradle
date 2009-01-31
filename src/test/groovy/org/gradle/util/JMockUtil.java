/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.util;

import org.jmock.Mockery;
import org.jmock.Expectations;
import org.gradle.api.dependencies.ConfigurationResolveInstructionModifier;
import org.gradle.api.dependencies.ConfigurationResolver;
import org.gradle.api.DependencyManager;

import java.io.File;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class JMockUtil {
    public static void configureResolve(Mockery context, final ConfigurationResolveInstructionModifier resolveInstructionModifier,
                                 final DependencyManager dependencyManagerMock, final ConfigurationResolver configurationMock,
                                 final List<File> classpath) {
        context.checking(new Expectations() {{
            allowing(dependencyManagerMock).configuration(resolveInstructionModifier.getConfiguration());
            will(returnValue(configurationMock));
            allowing(configurationMock).resolve(resolveInstructionModifier);
            will(returnValue(classpath));
        }});
    }
}
