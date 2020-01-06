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
import org.gradle.language.twirl.internal.DefaultTwirlTemplateFormat;
import org.gradle.scala.internal.reflect.ScalaCodecMapper;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class TwirlCompilerAdapterV10X extends VersionedTwirlCompilerAdapter {

    protected final String scalaVersion;
    protected final String twirlVersion;
    protected final VersionedPlayTwirlAdapter playTwirlAdapter;

    public TwirlCompilerAdapterV10X(String twirlVersion, String scalaVersion, VersionedPlayTwirlAdapter playTwirlAdapter) {
        this.scalaVersion = scalaVersion;
        this.twirlVersion = twirlVersion;
        this.playTwirlAdapter = playTwirlAdapter;
    }

    @Override
    public ScalaMethod getCompileMethod(ClassLoader cl) throws ClassNotFoundException {
        return ScalaReflectionUtil.scalaMethod(
                cl,
                "play.twirl.compiler.TwirlCompiler",
                "compile",
                File.class,
                File.class,
                File.class,
                String.class,
                String.class,
                cl.loadClass(ScalaCodecMapper.getClassName()),
                boolean.class,
                boolean.class
        );
    }

    @Override
    public Object[] createCompileParameters(ClassLoader cl, File file, File sourceDirectory, File destinationDirectory, TwirlImports defaultPlayImports, TwirlTemplateFormat templateFormat, List<String> additionalImports) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        final Collection<String> defaultImports = playTwirlAdapter.getDefaultImports(defaultPlayImports);
        return new Object[] {
                file,
                sourceDirectory,
                destinationDirectory,
                templateFormat.getFormatType(),
                getImportsFor(templateFormat, defaultImports, additionalImports),
                ScalaCodecMapper.create(cl, "UTF-8"),
                isInclusiveDots(),
                isUseOldParser()
        };
    }

    protected boolean isInclusiveDots() {
        return false;
    }

    private boolean isUseOldParser() {
        return false;
    }

    @Override
    public List<String> getDependencyNotation() {
        return Collections.singletonList("com.typesafe.play:twirl-compiler_" + scalaVersion + ":" + twirlVersion);
    }

    @Override
    public Collection<TwirlTemplateFormat> getDefaultTemplateFormats() {
        return Arrays.<TwirlTemplateFormat>asList(
            new DefaultTwirlTemplateFormat("html", "play.twirl.api.HtmlFormat", Collections.singleton("views.html._")),
            new DefaultTwirlTemplateFormat("txt", "play.twirl.api.TxtFormat", Collections.singleton("views.txt._")),
            new DefaultTwirlTemplateFormat("xml", "play.twirl.api.XmlFormat", Collections.singleton("views.xml._")),
            new DefaultTwirlTemplateFormat("js", "play.twirl.api.JavaScriptFormat", Collections.singleton("views.js._"))
        );
    }
}
