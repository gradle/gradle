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
    private static final Iterable<String> SHARED_PACKAGES = Arrays.asList("play.twirl.compiler", "scala.io"); //scala.io is for Codec which is a parameter to twirl

    // Based on https://github.com/playframework/playframework/blob/2.4.0/framework/src/build-link/src/main/java/play/TemplateImports.java
    private static final Collection<String> DEFAULT_JAVA_IMPORTS = Arrays.asList(
        "models._",
        "controllers._",
        "play.api.templates.PlayMagic._",
        "java.lang._",
        "java.util._",
        "scala.collection.JavaConversions._",
        "scala.collection.JavaConverters._",
        "play.api.i18n._",
        "play.core.j.PlayMagicForJava._",
        "play.mvc._",
        "play.data._",
        "play.api.data.Field",
        "play.mvc.Http.Context.Implicit._");

    private static final Collection<String> DEFAULT_SCALA_IMPORTS = Arrays.asList(
        "models._",
        "controllers._",
        "play.api.templates.PlayMagic._",
        "play.api.i18n._",
        "play.api.mvc._",
        "play.api.data._");

    private final String scalaVersion;
    private final String twirlVersion;

    public TwirlCompilerAdapterV10X(String twirlVersion, String scalaVersion) {
        this.scalaVersion = scalaVersion;
        this.twirlVersion = twirlVersion;
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
    public Object[] createCompileParameters(ClassLoader cl, final File file, File sourceDirectory, File destinationDirectory, TwirlImports defaultImports, TwirlTemplateFormat templateFormat, List<String> additionalImports) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        final Collection<String> defaultTwirlImports;
        if (defaultImports == TwirlImports.JAVA) {
            defaultTwirlImports = DEFAULT_JAVA_IMPORTS;
        } else {
            defaultTwirlImports = DEFAULT_SCALA_IMPORTS;
        }
        return new Object[] {
                file,
                sourceDirectory,
                destinationDirectory,
                templateFormat.getFormatType(),
                getImportsFor(templateFormat, defaultTwirlImports, additionalImports),
                ScalaCodecMapper.create(cl, "UTF-8"),
                isInclusiveDots(),
                isUseOldParser()
        };
    }

    private boolean isInclusiveDots() {
        return false;
    }

    private boolean isUseOldParser() {
        return false;
    }

    @Override
    public Iterable<String> getClassLoaderPackages() {
        return SHARED_PACKAGES;
    }

    @Override
    public String getDependencyNotation() {
        return "com.typesafe.play:twirl-compiler_" + scalaVersion + ":" + twirlVersion;
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
