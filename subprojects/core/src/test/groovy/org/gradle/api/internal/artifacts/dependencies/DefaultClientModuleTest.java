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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.InvalidUserDataException;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class DefaultClientModuleTest extends AbstractModuleDependencyTest {
    private static final String TEST_GROUP = "org.gradle";
    private static final String TEST_NAME = "gradle-core";
    private static final String TEST_VERSION = "4.4-beta2";

    DefaultClientModule clientModule;

    protected AbstractModuleDependency getDependency() {
        return clientModule;
    }

    protected AbstractModuleDependency createDependency(String group, String name, String version) {
        return new DefaultClientModule(group, name, version);
    }

    protected AbstractModuleDependency createDependency(String group, String name, String version, String configuration) {
        return new DefaultClientModule(group, name, version, configuration);
    }

    @Before
    public void setUp() {
        clientModule = new DefaultClientModule(TEST_GROUP, TEST_NAME, TEST_VERSION);
    }

    @Test
    public void init() {
        assertThat(clientModule.getGroup(), equalTo(TEST_GROUP));
        assertThat(clientModule.getName(), equalTo(TEST_NAME));
        assertThat(clientModule.getVersion(), equalTo(TEST_VERSION));
        assertThat(clientModule.isForce(), equalTo(false));
        assertThat(clientModule.isTransitive(), equalTo(true));
    }

    @Test(expected = InvalidUserDataException.class)
    public void initWithNullNameShouldThrowInvalidUserDataEx() {
        new DefaultClientModule(TEST_GROUP, null, TEST_VERSION);
    }

    @Test
    public void contentEqualsWithEqualDependencies() {
        DefaultClientModule clientModule1 = createModule();
        DefaultClientModule clientModule2 = createModule();
        assertThat(clientModule1.contentEquals(clientModule2), equalTo(true));
    }

    @Test
    public void contentEqualsWithNonEqualDependencies() {
        DefaultClientModule clientModule1 = createModule();
        DefaultClientModule clientModule2 = createModule();
        clientModule2.setGroup(clientModule1.getGroup() + "delta");
        assertThat(clientModule1.contentEquals(clientModule2), equalTo(false));
    }

    @Test
    public void copy() {
        DefaultClientModule clientModule = createModule();
        DefaultClientModule copiedClientModule = (DefaultClientModule) clientModule.copy();
        assertThat(clientModule.contentEquals(copiedClientModule), equalTo(true));
        assertDeepCopy(clientModule, copiedClientModule);
        assertThat(copiedClientModule.getDependencies().iterator().next(), not(sameInstance(clientModule.getDependencies().iterator().next())));
    }

    private DefaultClientModule createModule() {
        DefaultClientModule clientModule =  new DefaultClientModule("group", "name", "version", "conf");
        clientModule.addArtifact(new DefaultDependencyArtifact("name", "type", "ext", "classifier", "url"));
        clientModule.addDependency(new DefaultExternalModuleDependency("org", "name", "version"));
        return clientModule;
    }
}
