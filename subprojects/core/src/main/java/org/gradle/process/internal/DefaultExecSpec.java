/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.process.internal;

import com.google.common.base.Preconditions;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.CollectionPropertyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.ExecSpec;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class DefaultExecSpec extends DefaultProcessForkOptions implements ExecSpec {

    @Inject
    public DefaultExecSpec(ObjectFactory objectFactory, PathToFileResolver resolver) {
        super(objectFactory, resolver);
        getIgnoreExitValue().convention(false);
    }

    public void copyTo(ExecSpec targetSpec) {
        // Fork options
        super.copyTo(targetSpec);
        // BaseExecSpec
        copyBaseExecSpecTo(this, targetSpec);
        // ExecSpec
        targetSpec.getArgs().set(getArgs());
        targetSpec.getArgumentProviders().addAll(getArgumentProviders());
    }

    static void copyBaseExecSpecTo(BaseExecSpec source, BaseExecSpec target) {
        target.getIgnoreExitValue().set(source.getIgnoreExitValue());
        if (source.getStandardInput().isPresent()) {
            target.getStandardInput().set(source.getStandardInput());
        }
        if (source.getStandardOutput().isPresent()) {
            target.getStandardOutput().set(source.getStandardOutput());
        }
        if (source.getErrorOutput().isPresent()) {
            target.getErrorOutput().set(source.getErrorOutput());
        }
        if (source.getExecutable().isPresent()) {
            target.getExecutable().set(source.getExecutable());
        }
    }

    @Override
    public Provider<List<String>> getCommandLine() {
        return getExecutable().zip(getArgs(), (SerializableLambdas.SerializableBiFunction<String, List<String>, List<String>>) (executable, args) -> {
            List<String> allArgs = ExecHandleCommandLineCombiner.getAllArgs(Collections.emptyList(), args, getArgumentProviders().get());
            return ExecHandleCommandLineCombiner.getCommandLine(executable, allArgs);
        });
    }

    @Override
    public ExecSpec commandLine(Object... arguments) {
        commandLine(Arrays.asList(arguments));
        return this;
    }

    @Override
    public ExecSpec commandLine(Iterable<?> args) {
        Iterator<?> iterator = args.iterator();
        if (!iterator.hasNext()) {
            throw new IllegalArgumentException("Can't set an empty command line");
        }
        executable(iterator.next());
        getArgs().empty();
        while (iterator.hasNext()) {
            args(iterator.next());
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExecSpec args(Object... args) {
        for (Object arg : args) {
            if (arg instanceof Provider) {
                ((CollectionPropertyInternal<String, List<String>>) getArgs()).append(((Provider<?>) arg).map(Object::toString));
            } else {
                getArgs().add(Preconditions.checkNotNull(arg).toString());
            }
        }
        return this;
    }

    @Override
    public ExecSpec args(Iterable<?> args) {
        for (Object arg : args) {
            args(arg);
        }
        return this;
    }
}
