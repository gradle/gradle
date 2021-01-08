/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.test.internal;

import org.gradle.nativeplatform.NativeBinarySpec;
import org.gradle.nativeplatform.NativeExecutableFileSpec;
import org.gradle.nativeplatform.NativeInstallationSpec;
import org.gradle.nativeplatform.internal.AbstractNativeBinarySpec;
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary;
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.NativeTestSuiteSpec;
import org.gradle.nativeplatform.test.tasks.RunTestExecutable;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper;

import java.io.File;

public class DefaultNativeTestSuiteBinarySpec extends AbstractNativeBinarySpec implements NativeTestSuiteBinarySpecInternal {
    private final DefaultTasksCollection tasks = new DefaultTasksCollection(super.getTasks());
    private NativeBinarySpecInternal testedBinary;
    private NativeInstallationSpec installation = new NativeInstallationSpec();
    private NativeExecutableFileSpec executable = new NativeExecutableFileSpec();

    @Override
    public NativeTestSuiteSpec getComponent() {
        return getComponentAs(NativeTestSuiteSpec.class);
    }

    @Override
    public NativeTestSuiteSpec getTestSuite() {
        return getComponent();
    }

    @Override
    public NativeBinarySpec getTestedBinary() {
        return testedBinary;
    }

    @Override
    public void setTestedBinary(NativeBinarySpecInternal testedBinary) {
        this.testedBinary = testedBinary;
        setTargetPlatform(testedBinary.getTargetPlatform());
        setToolChain(testedBinary.getToolChain());
        setPlatformToolProvider(testedBinary.getPlatformToolProvider());
        setBuildType(testedBinary.getBuildType());
        setFlavor(testedBinary.getFlavor());
    }

    @Override
    public File getExecutableFile() {
        return getExecutable().getFile();
    }

    @Override
    public NativeInstallationSpec getInstallation() {
        return installation;
    }

    @Override
    public NativeExecutableFileSpec getExecutable() {
        return executable;
    }

    @Override
    public File getPrimaryOutput() {
        return getExecutableFile();
    }

    @Override
    protected ObjectFilesToBinary getCreateOrLink() {
        return tasks.getLink();
    }

    @Override
    public NativeTestSuiteBinarySpec.TasksCollection getTasks() {
        return tasks;
    }

    private static class DefaultTasksCollection extends BinaryTasksCollectionWrapper implements NativeTestSuiteBinarySpec.TasksCollection {
        public DefaultTasksCollection(BinaryTasksCollection delegate) {
            super(delegate);
        }

        @Override
        public LinkExecutable getLink() {
            return findSingleTaskWithType(LinkExecutable.class);
        }

        @Override
        public InstallExecutable getInstall() {
            return findSingleTaskWithType(InstallExecutable.class);
        }

        @Override
        public RunTestExecutable getRun() {
            return findSingleTaskWithType(RunTestExecutable.class);
        }
    }
}
