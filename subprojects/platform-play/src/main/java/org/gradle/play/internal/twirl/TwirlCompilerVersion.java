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

enum TwirlCompilerVersion {

    V_22X("play.templates.ScalaTemplateCompiler", "play.api.templates.HtmlFormat", DEFAULTS.V_22X_DEFAULT_IMPORTS),
    V_102("play.twirl.compiler.TwirlCompiler", "play.twirl.api.HtmlFormat", DEFAULTS.V_102_DEFAULT_IMPORTS);

    private final String compilerClassName;
    private String defaultFormatterType;
    private String defaultAdditionalImports;

    TwirlCompilerVersion(String compilerClassName, String defaultFormatterType, String defaultAdditionalImports) {
        this.compilerClassName = compilerClassName;
        this.defaultFormatterType = defaultFormatterType;
        this.defaultAdditionalImports = defaultAdditionalImports;
    }

    String getCompilerClassname(){
        return compilerClassName;
    }

    static TwirlCompilerVersion parse(String version){
        if(version.startsWith("2.2.")){
            return V_22X;
        }else if(version.equals("1.0.2")){
            return V_102;
        }
        return V_102; // DEFAULT fallback
    }

    public String getDefaultFormatterType() {
        return defaultFormatterType;
    }

    public String getDefaultAdditionalImports() {
        return defaultAdditionalImports;
    }

    //TODO: use either the Scala or the Java import.
    //TODO: fix hardcoded html format
    private static String defaultScalaImports22x(String format) {
        return String.format("import play.api.templates._; import play.api.templates.PlayMagic._; import models._; import controllers._; import play.api.i18n._; import play.api.mvc._; import play.api.data._; import views.%s._;", format);
    }
    private static String defaultJavaImports22x(String format) {
        return String.format("import play.api.templates._; import play.api.templates.PlayMagic._; import models._; import controllers._; import play.api.i18n._; import play.api.mvc._; import play.api.data._; import views.%s._;", format);
    }

    private static String defaultScalaImports102(String format) {
        return String.format("import models._;import controllers._;import play.api.i18n._;import play.api.mvc._;import play.api.data._;import views.%s._;", "html");
    }

    private static String defaultJavaImports102(String format) {
        return String.format("import models._;import controllers._;import java.lang._;import java.util._;import scala.collection.JavaConversions._;import scala.collection.JavaConverters._;import play.api.i18n._;import play.core.j.PlayMagicForJava._;import play.mvc._;import play.data._;import play.api.data.Field;import play.mvc.Http.Context.Implicit._;import views.%s._;", "html");
    }

    static class DEFAULTS {
        static final String V_22X_DEFAULT_IMPORTS = defaultScalaImports22x("html");

        static final String V_102_DEFAULT_IMPORTS = defaultScalaImports102("html");
    }
}