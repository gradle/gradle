/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.plugins.internal.ant.AntWorkAction;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.util.internal.VersionNumber;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public abstract class GroovydocAntAction extends AntWorkAction<GroovydocParameters> {
    @Override
    protected String getActionName() {
        return "groovydoc";
    }

    @Override
    protected Action<AntBuilderDelegate> getAntAction() {
        return new Action<AntBuilderDelegate>() {
            @Override
            public void execute(AntBuilderDelegate ant) {
                GroovydocParameters parameters = getParameters();

                final VersionNumber version = getGroovyVersion();

                final Map<String, Object> args = new LinkedHashMap<>();
                args.put("sourcepath", parameters.getTmpDir().get().getAsFile());
                args.put("destdir", parameters.getDestinationDirectory().get().getAsFile());
                args.put("use", parameters.getUse().get());
                if (isAtLeast(version, "2.4.6")) {
                    args.put("noTimestamp", parameters.getNoTimestamp().get());
                    args.put("noVersionStamp", parameters.getNoVersionStamp().get());
                }
                args.put(parameters.getAccess().get().name().toLowerCase(Locale.ROOT), true);

                args.put("author", parameters.getIncludeAuthor().get());
                if (isAtLeast(version, "1.7.3")) {
                    args.put("processScripts", parameters.getProcessScripts().get());
                    args.put("includeMainForScripts", parameters.getIncludeMainForScripts().get());
                }
                putIfNotNull(args, "windowtitle", parameters.getWindowTitle().getOrNull());
                putIfNotNull(args, "doctitle", parameters.getDocTitle().getOrNull());
                putIfNotNull(args, "header", parameters.getHeader().getOrNull());
                putIfNotNull(args, "footer", parameters.getFooter().getOrNull());
                putIfNotNull(args, "overview", parameters.getOverview().getOrNull());

                ant.invokeMethod("taskdef", ImmutableMap.of(
                    "name", "groovydoc",
                    "classname", "org.codehaus.groovy.ant.Groovydoc"
                ));

                ant.invokeMethod("groovydoc", new Object[]{args, new Closure<Object>(this, this) {
                    @SuppressWarnings("UnusedVariable")
                    public Object doCall(Object ignore) {
                        for (Groovydoc.Link link : parameters.getLinks().get()) {
                            ant.invokeMethod("link", new Object[]{
                                ImmutableMap.of(
                                    "packages", Joiner.on(",").join(link.getPackages()),
                                    "href", link.getUrl()
                                )
                            });
                        }

                        return null;
                    }
                }});
            }
        };
    }

    private static VersionNumber getGroovyVersion() {
        try {
            Class<?> groovySystem = Thread.currentThread().getContextClassLoader().loadClass("groovy.lang.GroovySystem");
            Method getVersion = groovySystem.getDeclaredMethod("getVersion");
            String versionString = (String) getVersion.invoke(null);
            return VersionNumber.parse(versionString);
        } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException | InvocationTargetException ex) {
            // ignore
        }
        return VersionNumber.UNKNOWN;
    }

    private static boolean isAtLeast(VersionNumber version, String versionString) {
        return version.compareTo(VersionNumber.parse(versionString)) >= 0;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, @Nullable Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
