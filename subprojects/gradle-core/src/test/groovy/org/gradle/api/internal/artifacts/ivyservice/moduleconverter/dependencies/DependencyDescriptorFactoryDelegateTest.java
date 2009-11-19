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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ProjectDependency;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class DependencyDescriptorFactoryDelegateTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    private String configurationName = "conf";
    private DefaultModuleDescriptor moduleDescriptorDummy = context.mock(DefaultModuleDescriptor.class);
    private ProjectDependency projectDependency = context.mock(ProjectDependency.class);

    @Test
    public void convertShouldDelegateToTypeSpecificFactory() {
        final DependencyDescriptorFactoryInternal dependencyDescriptorFactoryInternal1 = context.mock(DependencyDescriptorFactoryInternal.class, "factory1");
        final DependencyDescriptorFactoryInternal dependencyDescriptorFactoryInternal2 = context.mock(DependencyDescriptorFactoryInternal.class, "factory2");
        context.checking(new Expectations() {{
            allowing(dependencyDescriptorFactoryInternal1).canConvert(projectDependency);
            will(returnValue(false));
            allowing(dependencyDescriptorFactoryInternal2).canConvert(projectDependency);
            will(returnValue(true));
            one(dependencyDescriptorFactoryInternal2).addDependencyDescriptor(configurationName, moduleDescriptorDummy, projectDependency);
        }});
        DependencyDescriptorFactoryDelegate dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
            dependencyDescriptorFactoryInternal1, dependencyDescriptorFactoryInternal2            
        );
        dependencyDescriptorFactoryDelegate.addDependencyDescriptor(configurationName, moduleDescriptorDummy, projectDependency);
    }

    @Test(expected = InvalidUserDataException.class)
    public void convertShouldThrowExForUnknownDependencyType() {
        final DependencyDescriptorFactoryInternal dependencyDescriptorFactoryInternal1 = context.mock(DependencyDescriptorFactoryInternal.class, "factory1");
        context.checking(new Expectations() {{
            allowing(dependencyDescriptorFactoryInternal1).canConvert(projectDependency);
            will(returnValue(false));
        }});
        DependencyDescriptorFactoryDelegate dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
            dependencyDescriptorFactoryInternal1            
        );
        dependencyDescriptorFactoryDelegate.addDependencyDescriptor(configurationName, moduleDescriptorDummy, projectDependency);
    }
}
