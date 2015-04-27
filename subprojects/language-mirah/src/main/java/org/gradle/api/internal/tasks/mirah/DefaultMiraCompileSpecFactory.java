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

package org.gradle.api.internal.tasks.mirah;

import org.gradle.api.internal.tasks.compile.AbstractJavaCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.ForkingJavaCompileSpec;
import org.gradle.api.tasks.compile.CompileOptions;

public class DefaultMirahCompileSpecFactory extends AbstractJavaCompileSpecFactory<DefaultMirahCompileSpec> {
    public DefaultMirahCompileSpecFactory(CompileOptions compileOptions) {
        super(compileOptions);
    }

    @Override
    protected DefaultMirahCompileSpec getCommandLineSpec() {
        return new DefaultCommandLineMirahCompileSpec();
    }

    @Override
    protected DefaultMirahCompileSpec getForkingSpec() {
        return new DefaultForkingMirahCompileSpec();
    }

    @Override
    protected DefaultMirahCompileSpec getDefaultSpec() {
        return new DefaultMirahCompileSpec();
    }

    private static class DefaultCommandLineMirahCompileSpec extends DefaultMirahCompileSpec implements CommandLineJavaCompileSpec {
    }

    private static class DefaultForkingMirahCompileSpec extends DefaultMirahCompileSpec implements ForkingJavaCompileSpec {
    }
}
