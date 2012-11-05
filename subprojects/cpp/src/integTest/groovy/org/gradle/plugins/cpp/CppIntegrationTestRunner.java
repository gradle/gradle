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

package org.gradle.plugins.cpp;

import com.google.common.base.Joiner;
import org.gradle.integtests.fixtures.AbstractMultiTestRunner;
import org.gradle.internal.nativeplatform.*;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.util.List;
import java.util.Map;

public class CppIntegrationTestRunner extends AbstractMultiTestRunner {
    public CppIntegrationTestRunner(Class<?> target) {
        super(target);
    }

    @Override
    protected void createExecutions() {
        List<AvailableCompilers.CompilerCandidate> compilers = AvailableCompilers.getCompilers();
        for (AvailableCompilers.CompilerCandidate compiler : compilers) {
            add(new CompilerExecution(compiler));
        }
    }

    private static class CompilerExecution extends Execution {
        private static final ProcessEnvironment PROCESS_ENVIRONMENT = NativeServices.getInstance().get(ProcessEnvironment.class);
        private final AvailableCompilers.CompilerCandidate compiler;
        private String originalPath;
        private final String pathVarName;


        public CompilerExecution(AvailableCompilers.CompilerCandidate compiler) {
            this.compiler = compiler;
            this.pathVarName = !OperatingSystem.current().isWindows() ? "Path" : "PATH";
        }

        @Override
        protected boolean isEnabled() {
            return compiler.isAvailable() && canDoNecessaryEnvironmentManipulation();
        }

        private boolean canDoNecessaryEnvironmentManipulation() {
            return (compiler.getEnvironmentVars().isEmpty() && compiler.getPathEntries().isEmpty())
                    || PROCESS_ENVIRONMENT.maybeSetEnvironmentVariable(pathVarName, System.getenv(pathVarName));
        }

        @Override
        protected String getDisplayName() {
            return compiler.getDisplayName();
        }

        @Override
        protected void before() {
            System.out.println(String.format("Using compiler %s", compiler.getDisplayName()));

            String compilerPath = Joiner.on(File.pathSeparator).join(compiler.getPathEntries());


            if (compilerPath.length() > 0) {
                originalPath = System.getenv(pathVarName);
                String path = compilerPath + File.pathSeparator + originalPath;
                System.out.println(String.format("Using path %s", path));
                PROCESS_ENVIRONMENT.setEnvironmentVariable(pathVarName, path);
            }

            for (Map.Entry<String, String> entry : compiler.getEnvironmentVars().entrySet()) {
                System.out.println(String.format("Using environment var %s -> %s", entry.getKey(), entry.getValue()));
                PROCESS_ENVIRONMENT.setEnvironmentVariable(entry.getKey(), entry.getValue());
            }
        }

        @Override
        protected void after() {
            if (originalPath != null) {
                PROCESS_ENVIRONMENT.setEnvironmentVariable(pathVarName, originalPath);
            }
        }
    }
}
