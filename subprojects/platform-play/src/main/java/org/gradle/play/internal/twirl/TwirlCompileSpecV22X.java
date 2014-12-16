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

import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

public class TwirlCompileSpecV22X extends DefaultVersionedTwirlCompileSpec implements VersionedTwirlCompileSpec {

    private final String twirlVersion;
    private final String scalaVersion;

    public TwirlCompileSpecV22X(File sourceDirectory, Iterable<File> sources, File destinationDirectory, BaseForkOptions forkOptions, boolean javaProject, String twirlVersion, String scalaVersion) {
        super(sourceDirectory, sources, destinationDirectory, forkOptions, javaProject);
        this.twirlVersion = twirlVersion;
        this.scalaVersion = scalaVersion;
    }

    @Override
    protected String defaultFormatterType() {
        return "play.api.templates.HtmlFormat";
    }

    @Override
    protected String defaultJavaAdditionalImports(String format) {
        return String.format("import play.api.templates._; import play.api.templates.PlayMagic._; import models._; import controllers._; import play.api.i18n._; import play.api.mvc._; import play.api.data._; import views.%s._;", format);
    }

    @Override
    protected String defaultScalaAdditionalImports(String format) {
        return String.format("import play.api.templates._; import play.api.templates.PlayMagic._; import models._; import controllers._; import play.api.i18n._; import play.api.mvc._; import play.api.data._; import views.%s._;", format);
    }

    public ScalaMethod getCompileMethod(final ClassLoader cl) throws ClassNotFoundException {
        return ScalaReflectionUtil.scalaMethod(
                cl,
                "play.templates.ScalaTemplateCompiler",
                "compile",
                File.class,
                File.class,
                File.class,
                String.class,
                String.class
        );
    }

    public Object[] createCompileParameters(ClassLoader cl, File file) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return new Object[] {
                file,
                getSourceDirectory(),
                getDestinationDir(),
                getFormatterType(),
                getAdditionalImports()
        };
    }

    public List<String> getClassLoaderPackages() {
        return Arrays.asList("play.templates");
    }

    public Object getDependencyNotation() {
        return String.format("com.typesafe.play:templates-compiler_%s:%s", scalaVersion, twirlVersion);
    }
}
