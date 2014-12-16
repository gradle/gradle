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
import org.gradle.scala.internal.reflect.ScalaCodecMapper;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

public class TwirlCompileSpecV10X extends DefaultVersionedTwirlCompileSpec implements VersionedTwirlCompileSpec {

    private final String scalaVersion;
    private final String twirlVersion;
    private String codec = "UTF-8";
    private boolean inclusiveDots;
    private boolean useOldParser;

    public TwirlCompileSpecV10X(File sourceDirectory, Iterable<File> sources, File destinationDirectory, BaseForkOptions forkOptions, boolean javaProject, String twirlVersion, String scalaVersion) {
        super(sourceDirectory, sources, destinationDirectory, forkOptions, javaProject);
        this.scalaVersion = scalaVersion;
        this.twirlVersion = twirlVersion;
    }

    protected String defaultFormatterType() {
        return "play.twirl.api.HtmlFormat";
    }

    protected String defaultScalaAdditionalImports(String format) {
        return String.format("import models._;import controllers._;import play.api.i18n._;import play.api.mvc._;import play.api.data._;import views.%s._;", "html");
    }

    protected String defaultJavaAdditionalImports(String format) {
        return String.format("import models._;import controllers._;import java.lang._;import java.util._;import scala.collection.JavaConversions._;import scala.collection.JavaConverters._;import play.api.i18n._;import play.core.j.PlayMagicForJava._;import play.mvc._;import play.data._;import play.api.data.Field;import play.mvc.Http.Context.Implicit._;import views.%s._;", "html");
    }

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

    public Object[] createCompileParameters(ClassLoader cl, File file) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return new Object[] {
                file,
                getSourceDirectory(),
                getDestinationDir(),
                getFormatterType(),
                getAdditionalImports(),
                ScalaCodecMapper.create(cl, codec),
                inclusiveDots,
                useOldParser
        };
    }

    public List<String> getClassLoaderPackages() {
        return Arrays.asList("play.twirl.compiler", "scala.io"); //scala.io is for Codec which is a parameter to twirl
    }

    public Object getDependencyNotation() {
        return String.format("com.typesafe.play:twirl-compiler_%s:%s", scalaVersion, twirlVersion);
    }
}
