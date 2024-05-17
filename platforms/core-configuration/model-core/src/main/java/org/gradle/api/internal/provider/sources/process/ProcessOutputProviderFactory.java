/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.provider.sources.process;

import org.gradle.api.Action;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;

@ServiceScope(Scopes.Build.class)
public class ProcessOutputProviderFactory {
    private final Instantiator instantiator;
    private final ExecSpecFactory execSpecFactory;

    public ProcessOutputProviderFactory(Instantiator instantiator, ExecSpecFactory execSpecFactory) {
        this.instantiator = instantiator;
        this.execSpecFactory = execSpecFactory;
    }

    public void configureParametersForExec(ProcessOutputValueSource.Parameters parameters, Action<? super ExecSpec> action) {
        configureParameters(parameters, instantiator.newInstance(ProviderCompatibleExecSpec.class, execSpecFactory.newExecSpec()), action);
    }

    public void configureParametersForJavaExec(ProcessOutputValueSource.Parameters parameters, Action<? super JavaExecSpec> action) {
        configureParameters(parameters, instantiator.newInstance(ProviderCompatibleJavaExecSpec.class, execSpecFactory.newJavaExecSpec()), action);
    }

    private <T extends ProviderCompatibleBaseExecSpec> void configureParameters(ProcessOutputValueSource.Parameters parameters, T spec, Action<? super T> action) {
        action.execute(spec);
        spec.copyToParameters(parameters);
    }
}
