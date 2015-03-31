/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.plugins;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.util.CollectionUtils;
import org.gradle.util.TextUtil;

import java.util.HashMap;
import java.util.Map;

public class UnixStartScriptGenerator extends AbstractTemplateBasedStartScriptGenerator {
    String getDefaultTemplateFilename() {
        return "unixStartScript.txt";
    }

    String getLineSeparator() {
        return TextUtil.getUnixLineSeparator();
    }

    public Map<String, String> createBinding(JavaAppStartScriptGenerationDetails details) {
        Map<String, String> binding = new HashMap<String, String>();
        binding.put(ScriptBindingParameter.APP_NAME.getKey(), details.getApplicationName());
        binding.put(ScriptBindingParameter.OPTS_ENV_VAR.getKey(), details.getOptsEnvironmentVar());
        binding.put(ScriptBindingParameter.MAIN_CLASSNAME.getKey(), details.getMainClassName());
        binding.put(ScriptBindingParameter.DEFAULT_JVM_OPTS.getKey(), createJoinedDefaultJvmOpts(details.getDefaultJvmOpts()));
        binding.put(ScriptBindingParameter.APP_NAME_SYS_PROP.getKey(), details.getAppNameSystemProperty());
        binding.put(ScriptBindingParameter.APP_HOME_REL_PATH.getKey(), createJoinedAppHomeRelativePath(details.getScriptRelPath()));
        binding.put(ScriptBindingParameter.CLASSPATH.getKey(), createJoinedClasspath(details.getClasspath()));
        return binding;
    }

    private String createJoinedClasspath(Iterable<String> classpath) {
        Joiner colonJoiner = Joiner.on(":");
        return colonJoiner.join(Iterables.transform(CollectionUtils.toStringList(classpath), new Function<String, String>() {
            public String apply(String input) {
                StringBuilder classpath = new StringBuilder();
                classpath.append("$APP_HOME/");
                classpath.append(input.replace("\\", "/"));
                return classpath.toString();
            }
        }));
    }

    private String createJoinedDefaultJvmOpts(Iterable<String> defaultJvmOpts) {
        if(defaultJvmOpts == null) {
            return "";
        }

        Iterable<String> quotedDefaultJvmOpts = Iterables.transform(CollectionUtils.toStringList(defaultJvmOpts), new Function<String, String>() {
            public String apply(String jvmOpt) {
                //quote ', ", \, $. Probably not perfect. TODO: identify non-working cases, fail-fast on them
                jvmOpt = jvmOpt.replace("\\", "\\\\");
                jvmOpt = jvmOpt.replace("\"", "\\\"");
                jvmOpt = jvmOpt.replace("'", "'\"'\"'");
                jvmOpt = jvmOpt.replace("`", "'\"`\"'");
                jvmOpt = jvmOpt.replace("$", "\\$");
                StringBuilder quotedJvmOpt = new StringBuilder();
                quotedJvmOpt.append("\"");
                quotedJvmOpt.append(jvmOpt);
                quotedJvmOpt.append("\"");
                return quotedJvmOpt.toString();
            }
        });

        //put the whole arguments string in single quotes, unless defaultJvmOpts was empty,
        // in which case we output "" to stay compatible with existing builds that scan the script for it
        Joiner spaceJoiner = Joiner.on(" ");
        if(Iterables.size(quotedDefaultJvmOpts) > 0) {
            StringBuilder singleQuoteJvmOpt = new StringBuilder();
            singleQuoteJvmOpt.append("'");
            singleQuoteJvmOpt.append(spaceJoiner.join(quotedDefaultJvmOpts));
            singleQuoteJvmOpt.append("'");
            return singleQuoteJvmOpt.toString();
        }

        return "\"\"";
    }
}
