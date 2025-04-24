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
import com.google.common.collect.Streams;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Transformer;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.util.internal.CollectionUtils;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StartScriptTemplateBindingFactory implements Transformer<Map<String, String>, JavaAppStartScriptGenerationDetails> {

    private final boolean windows;

    private StartScriptTemplateBindingFactory(boolean windows) {
        this.windows = windows;
    }

    public static StartScriptTemplateBindingFactory windows() {
        return new StartScriptTemplateBindingFactory(true);
    }

    public static StartScriptTemplateBindingFactory unix() {
        return new StartScriptTemplateBindingFactory(false);
    }

    @Override
    public Map<String, String> transform(JavaAppStartScriptGenerationDetails details) {
        Map<String, String> binding = new HashMap<>();
        // Before changing, see the note in ScriptBindingParameter's Javadoc
        binding.put(ScriptBindingParameter.APP_NAME.getKey(), details.getApplicationName());
        binding.put(ScriptBindingParameter.OPTS_ENV_VAR.getKey(), details.getOptsEnvironmentVar());
        binding.put(ScriptBindingParameter.EXIT_ENV_VAR.getKey(), details.getExitEnvironmentVar());

        AppEntryPoint entryPoint = getEntryPoint(details);
        String entryPointArgs = encodeEntryPoint(entryPoint);
        binding.put(ScriptBindingParameter.ENTRY_POINT_ARGS.getKey(), entryPointArgs);
        binding.put(ScriptBindingParameter.MAIN_CLASS_NAME.getKey(), getMainClassName(entryPoint, entryPointArgs));
        binding.put(ScriptBindingParameter.MODULE_ENTRY_POINT.getKey(), entryPoint instanceof MainModule ? getModuleEntryPoint((MainModule) entryPoint) : null);

        binding.put(ScriptBindingParameter.DEFAULT_JVM_OPTS.getKey(), createJoinedDefaultJvmOpts(details.getDefaultJvmOpts()));
        binding.put(ScriptBindingParameter.APP_NAME_SYS_PROP.getKey(), details.getAppNameSystemProperty());
        binding.put(ScriptBindingParameter.APP_HOME_REL_PATH.getKey(), createJoinedAppHomeRelativePath(details.getScriptRelPath()));
        binding.put(ScriptBindingParameter.CLASSPATH.getKey(), createJoinedPath(details.getClasspath()));
        binding.put(ScriptBindingParameter.MODULE_PATH.getKey(), createJoinedPath(details.getModulePath()));
        return binding;

    }

    private static String getMainClassName(AppEntryPoint entryPoint, String entryPointArgs) {
        if (entryPoint instanceof MainClass) {
            return ((MainClass) entryPoint).getMainClassName();
        } else if (entryPoint instanceof MainModule) {
            // For legacy reasons, keep the mainClassName as the module invocation for scripts which used it that way
            return entryPointArgs;
        } else {
            return "";
        }
    }

    private static AppEntryPoint getEntryPoint(JavaAppStartScriptGenerationDetails details) {
        if (details instanceof DefaultJavaAppStartScriptGenerationDetails) {
            return ((DefaultJavaAppStartScriptGenerationDetails) details).getEntryPoint();
        } else {
            // Provide compatibility in case someone was manually implementing JavaAppStartScriptGenerationDetails
            return new MainClass(details.getMainClassName());
        }
    }

    private String encodeEntryPoint(AppEntryPoint entryPoint) {
        if (entryPoint instanceof MainClass) {
            return ((MainClass) entryPoint).getMainClassName();
        } else if (entryPoint instanceof MainModule) {
            return "--module " + getModuleEntryPoint((MainModule) entryPoint);
        } else if (entryPoint instanceof ExecutableJar) {
            // We need to also escape quotes in the JAR path, so our quotes aren't broken by the JAR path
            // In theory we should be doing this in `encodePath`, but I wanted to avoid making behavioral changes to `classpath` and `modulePath`
            String jarPathEscaped = encodePath(((ExecutableJar) entryPoint).getJarPath()).replace("\"", "\\\"");
            return "-jar \"" + jarPathEscaped + "\"";
        } else {
            throw new IllegalArgumentException("Unknown entry point type: " + entryPoint);
        }
    }

    private static String getModuleEntryPoint(MainModule entryPoint) {
        String mainClassName = entryPoint.getMainClassName();
        boolean hasMainClass = mainClassName != null;
        return entryPoint.getMainModuleName() + (hasMainClass ? "/" + mainClassName : "");
    }

    private String createJoinedPath(Iterable<String> path) {
        if (!windows && !path.iterator().hasNext()) {
            return "\"\\\\\\\"\\\\\\\"\""; // empty path argument for shell script
        }
        return Streams.stream(path).map(this::encodePath).collect(Collectors.joining(getMultiPathSeparator()));
    }

    private String encodePath(String path) {
        if (windows) {
            return "%APP_HOME%\\" + path.replace("/", "\\");
        } else {
            return "$APP_HOME/" + path.replace("\\", "/");
        }
    }

    private String createJoinedDefaultJvmOpts(Iterable<String> defaultJvmOpts) {
        if (windows) {
            if (defaultJvmOpts == null) {
                return "";
            }

            Iterable<String> quotedDefaultJvmOpts = Iterables.transform(CollectionUtils.toStringList(defaultJvmOpts), new Function<String, String>() {
                @Override
                public String apply(String jvmOpt) {
                    return "\"" + escapeWindowsJvmOpt(jvmOpt) + "\"";
                }
            });

            Joiner spaceJoiner = Joiner.on(" ");
            return spaceJoiner.join(quotedDefaultJvmOpts);
        } else {
            if (defaultJvmOpts == null) {
                return "";
            }

            Iterable<String> quotedDefaultJvmOpts = Iterables.transform(CollectionUtils.toStringList(defaultJvmOpts), new Function<String, String>() {
                @Override
                public String apply(String jvmOpt) {
                    //quote ', ", \, $. Probably not perfect. TODO: identify non-working cases, fail-fast on them
                    jvmOpt = jvmOpt.replace("\\", "\\\\");
                    jvmOpt = jvmOpt.replace("\"", "\\\"");
                    jvmOpt = jvmOpt.replace("'", "'\"'\"'");
                    jvmOpt = jvmOpt.replace("`", "'\"`\"'");
                    jvmOpt = jvmOpt.replace("$", "\\$");
                    return "\"" + jvmOpt + "\"";
                }
            });

            //put the whole arguments string in single quotes, unless defaultJvmOpts was empty,
            // in which case we output "" to stay compatible with existing builds that scan the script for it
            Joiner spaceJoiner = Joiner.on(" ");
            if (Iterables.size(quotedDefaultJvmOpts) > 0) {
                return "'" + spaceJoiner.join(quotedDefaultJvmOpts) + "'";
            }

            return "\"\"";
        }
    }

    private String escapeWindowsJvmOpt(String jvmOpts) {
        boolean wasOnBackslash = false;
        StringBuilder escapedJvmOpt = new StringBuilder();
        CharacterIterator it = new StringCharacterIterator(jvmOpts);

        //argument quoting:
        // - " must be encoded as \"
        // - % must be encoded as %%
        // - pathological case: \" must be encoded as \\\", but other than that, \ MUST NOT be quoted
        // - other characters (including ') will not be quoted
        // - use a state machine rather than regexps
        for (char ch = it.first(); ch != CharacterIterator.DONE; ch = it.next()) {
            String repl = Character.toString(ch);

            if (ch == '%') {
                repl = "%%";
            } else if (ch == '"') {
                repl = (wasOnBackslash ? '\\' : "") + "\\\"";
            }
            wasOnBackslash = ch == '\\';
            escapedJvmOpt.append(repl);
        }

        return escapedJvmOpt.toString();
    }

    /**
     * @implNote These names and their behavior are public API, documented in {@link org.gradle.jvm.application.tasks.CreateStartScripts}.
     * Changes to these names or their behavior must be made carefully to avoid breaking existing custom script templates. Please update the documentation if you change them.
     */
    private enum ScriptBindingParameter {
        APP_NAME("applicationName"),
        OPTS_ENV_VAR("optsEnvironmentVar"),
        EXIT_ENV_VAR("exitEnvironmentVar"),
        MODULE_ENTRY_POINT("moduleEntryPoint"),
        MAIN_CLASS_NAME("mainClassName"),
        ENTRY_POINT_ARGS("entryPointArgs"),
        DEFAULT_JVM_OPTS("defaultJvmOpts"),
        APP_NAME_SYS_PROP("appNameSystemProperty"),
        APP_HOME_REL_PATH("appHomeRelativePath"),
        CLASSPATH("classpath"),
        MODULE_PATH("modulePath");

        private final String key;

        ScriptBindingParameter(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    String createJoinedAppHomeRelativePath(String scriptRelPath) {
        int depth = StringUtils.countMatches(scriptRelPath, "/");
        if (depth == 0) {
            return "";
        }

        List<String> appHomeRelativePath = new ArrayList<String>(depth);
        for (int i = 0; i < depth; i++) {
            appHomeRelativePath.add("..");
        }

        return Joiner.on(getPathElementSeparator()).join(appHomeRelativePath);
    }

    /**
     * The separator used to separate each element in a path.
     *
     * @return the path element separator
     */
    private String getPathElementSeparator() {
        return windows ? "\\" : "/";
    }

    /**
     * The separator used to separate each path in a multi-path argument.
     *
     * @return the multi-path separator
     */
    private String getMultiPathSeparator() {
        return windows ? ";" : ":";
    }

}
