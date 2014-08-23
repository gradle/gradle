/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.java.internal;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.tasks.DefaultJavaToolChain;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.JavaCompilerFactory;
import org.gradle.api.internal.tasks.compile.daemon.CompilerClientsManager;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager;
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonStarter;
import org.gradle.internal.Factory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.WorkerProcessBuilder;

public class JavaToolChainServiceRegistry implements PluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeCompileServices());
    }

    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeCompileServices());
    }

    private static class BuildScopeCompileServices {
        CompilerDaemonManager createCompilerDaemonManager(Factory<WorkerProcessBuilder> workerFactory, StartParameter startParameter) {
            return new CompilerDaemonManager(new CompilerClientsManager(new CompilerDaemonStarter(workerFactory, startParameter)));
        }
    }

    private static class ProjectScopeCompileServices {
        JavaCompilerFactory createJavaCompilerFactory(GradleInternal gradle, CompilerDaemonManager compilerDaemonManager) {
            return new DefaultJavaCompilerFactory(gradle.getRootProject().getProjectDir(), compilerDaemonManager);
        }

        JavaToolChainInternal createJavaToolChain(JavaCompilerFactory compilerFactory, ExecActionFactory execActionFactory) {
            return new DefaultJavaToolChain(compilerFactory, execActionFactory);
        }
    }
}
