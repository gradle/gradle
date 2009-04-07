/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.plugins.jetty;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.bundling.War;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Map;

/**
 * <p>A {@link Plugin} which extends the {@link WarPlugin} to add tasks which run the web application under Jetty.</p>
 * 
 * @author Hans Dockter
 */
public class JettyPlugin implements Plugin {
    public static final String JETTY_RUN = "jettyRun";
    public static final String JETTY_RUN_WAR = "jettyRunWar";
    public static final String JETTY_RUN_EXPLODED_WAR = "jettyRunExploded";
    public static final String JETTY_STOP = "jettyStop";

    public static final String RELOAD_AUTOMATIC = "automatic";
    public static final String RELOAD_MANUAL = "manual";

    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(WarPlugin.class, project, customValues);
        JettyPluginConvention jettyConvention = new JettyPluginConvention();
        Convention convention = project.getConvention();
        convention.getPlugins().put("jetty", jettyConvention);

        configureJettyRun(project, jettyConvention);
        configureJettyRunWar(project, jettyConvention);
        configureJettyRunWarExploded(project, jettyConvention);
        configureJettyStop(project, jettyConvention);
    }

    private void configureJettyRunWarExploded(Project project, JettyPluginConvention jettyConvention) {
        JettyRunWarExploded jettyRunWarExploded = (JettyRunWarExploded)
                 project.createTask(GUtil.map("type", JettyRunWarExploded.class), JETTY_RUN_EXPLODED_WAR);
        configureAbstractJettyTask(project, jettyConvention, jettyRunWarExploded);
    }

    private void configureJettyRunWar(final Project project, JettyPluginConvention jettyConvention) {
        JettyRunWar jettyRunWar = (JettyRunWar) project.createTask(GUtil.map("type", JettyRunWar.class), JETTY_RUN_WAR);
        jettyRunWar.dependsOn("archive_war");

        configureAbstractJettyTask(project, jettyConvention, jettyRunWar);
        jettyRunWar.getConventionMapping().put("webApp", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return ((War) project.task("archive_war")).getArchivePath();
            }
        });
    }

    private void configureJettyStop(Project project, final JettyPluginConvention jettyConvention) {
        JettyStop jettyStop = (JettyStop) project.createTask(GUtil.map("type", JettyStop.class), JETTY_STOP);
        jettyStop.getConventionMapping().put("stopPort", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return jettyConvention.getStopPort();
            }
        });
        jettyStop.getConventionMapping().put("stopKey", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return jettyConvention.getStopKey();
            }
        });
    }

    private void configureJettyRun(final Project project, final JettyPluginConvention jettyConvention) {
        JettyRun jettyRun = (JettyRun) project.createTask(GUtil.map("type", JettyRun.class), JETTY_RUN);
        jettyRun.dependsOn(JavaPlugin.TEST_COMPILE);

        configureAbstractJettyTask(project, jettyConvention, jettyRun);

        jettyRun.setConfiguration(JavaPlugin.RUNTIME);
        jettyRun.setTestConfiguration(JavaPlugin.TEST_RUNTIME);
        jettyRun.setUseTestClasspath(false);
        jettyRun.getConventionMapping().put("webXml", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return getWebXml(project);
            }
        });
        jettyRun.getConventionMapping().put("classesDirectory", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return getJavaConvention(project).getClassesDir();
            }
        });
        jettyRun.getConventionMapping().put("testClassesDirectory", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return getJavaConvention(project).getTestClassesDir();
            }
        });
        jettyRun.getConventionMapping().put("webAppSourceDirectory", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return getJavaConvention(project).getWebAppDir();
            }
        });
    }

    private Object getWebXml(Project project) {
        War war = (War) project.task("archive_war");
        File webXml = null;
        if (war.getWebXml() != null) {
            webXml = new File(war.getWebXml().toString());
        } else {
            webXml = new File(getJavaConvention(project).getWebAppDir(), "WEB-INF/web.xml");
        }
        return webXml;
    }

    private void configureAbstractJettyTask(final Project project, final JettyPluginConvention jettyConvention, AbstractJettyRunTask jettyTask) {
        jettyTask.setDaemon(false);
        jettyTask.setReload(RELOAD_AUTOMATIC);
        jettyTask.setScanIntervalSeconds(0);
        jettyTask.getConventionMapping().put("contextPath", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return ((War) project.task("archive_war")).getBaseName();
            }
        });
        jettyTask.getConventionMapping().put("tmpDirectory", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return new File(project.getBuildDir(), "jetty");
            }
        });
        jettyTask.getConventionMapping().put("stopPort", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return jettyConvention.getStopPort();
            }
        });
        jettyTask.getConventionMapping().put("stopKey", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return jettyConvention.getStopKey();
            }
        });
    }

    public JavaPluginConvention getJavaConvention(Project project) {
        return project.getConvention().getPlugin(JavaPluginConvention.class);
    }
}
