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

import org.gradle.language.twirl.TwirlImports;
import org.gradle.language.twirl.TwirlTemplateFormat;
import org.gradle.scala.internal.reflect.ScalaMethod;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

public abstract class VersionedTwirlCompilerAdapter implements Serializable {
    public abstract List<String> getDependencyNotation();

    public abstract ScalaMethod getCompileMethod(ClassLoader cl) throws ClassNotFoundException;

    public abstract Object[] createCompileParameters(ClassLoader cl, File file, File sourceDirectory, File destinationDirectory, TwirlImports defaultImports, TwirlTemplateFormat templateFormat, List<String> additionalImports) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException;

    public abstract Collection<TwirlTemplateFormat> getDefaultTemplateFormats();

    protected String getImportsFor(TwirlTemplateFormat templateFormat, Collection<String> defaultImports, Collection<String> additionalImports) {
        StringBuilder sb = new StringBuilder();
        addImports(sb, defaultImports);
        addImports(sb, templateFormat.getTemplateImports());
        addImports(sb, additionalImports);

        return sb.toString();
    }

    private void addImports(StringBuilder sb, Collection<String> imports) {
        for(String importPackage : imports) {
            sb.append("import ").append(importPackage).append(";\n");
        }
    }
}
