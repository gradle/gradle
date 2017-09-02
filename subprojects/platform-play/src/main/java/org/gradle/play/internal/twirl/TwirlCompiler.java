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

package org.gradle.play.internal.twirl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.FileUtils;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.twirl.TwirlTemplateFormat;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaOptionInvocationWrapper;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Twirl compiler uses reflection to load and invoke the actual compiler classes/methods.
 */
public class TwirlCompiler implements Compiler<TwirlCompileSpec>, Serializable {

    private final VersionedTwirlCompilerAdapter adapter;

    public TwirlCompiler(VersionedTwirlCompilerAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public WorkResult execute(TwirlCompileSpec spec) {
        List<File> outputFiles = Lists.newArrayList();
        ClassLoader cl = getClass().getClassLoader();
        ScalaMethod compile = getCompileMethod(cl);
        Iterable<RelativeFile> sources = spec.getSources();

        for (RelativeFile sourceFile : sources) {
            TwirlTemplateFormat format = findTemplateFormat(spec, sourceFile.getFile());
            try {
                Object result = compile.invoke(buildCompileArguments(spec, cl, sourceFile, format));
                ScalaOptionInvocationWrapper<File> maybeFile = new ScalaOptionInvocationWrapper<File>(result);
                if (maybeFile.isDefined()) {
                    File outputFile = maybeFile.get();
                    outputFiles.add(outputFile);
                }
            } catch (Exception e) {
                throw new RuntimeException("Error invoking Play Twirl template compiler.", e);
            }
        }

        return WorkResults.didWork(!outputFiles.isEmpty());
    }

    private ScalaMethod getCompileMethod(ClassLoader cl) {
        try {
            return adapter.getCompileMethod(cl);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error invoking Play Twirl template compiler.", e);
        }
    }

    private Object[] buildCompileArguments(TwirlCompileSpec spec, ClassLoader cl, RelativeFile sourceFile, TwirlTemplateFormat format) {
        try {
            return adapter.createCompileParameters(cl, sourceFile.getFile(), sourceFile.getBaseDir(), spec.getDestinationDir(), spec.getDefaultImports(), format, spec.getAdditionalImports());
        } catch (Exception e) {
            throw new RuntimeException("Error invoking Play Twirl template compiler.", e);
        }
    }

    private TwirlTemplateFormat findTemplateFormat(TwirlCompileSpec spec, final File sourceFile) {
        Spec<TwirlTemplateFormat> hasExtension = new Spec<TwirlTemplateFormat>() {
            @Override
            public boolean isSatisfiedBy(TwirlTemplateFormat format) {
                return FileUtils.hasExtensionIgnoresCase(sourceFile.getName(), "." + format.getExtension());
            }
        };

        TwirlTemplateFormat format = CollectionUtils.findFirst(adapter.getDefaultTemplateFormats(), hasExtension);
        if (format == null) {
            format = CollectionUtils.findFirst(spec.getUserTemplateFormats(), hasExtension);
        }

        Preconditions.checkNotNull(format, "Twirl compiler could not find a matching template for '%s'.", sourceFile.getName());

        return format;
    }

    public Object getDependencyNotation() {
        return adapter.getDependencyNotation();
    }

    public Iterable<String> getClassLoaderPackages() {
        return adapter.getClassLoaderPackages();
    }
}
