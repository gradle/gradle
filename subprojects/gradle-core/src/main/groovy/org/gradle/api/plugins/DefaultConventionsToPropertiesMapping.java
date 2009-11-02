/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.internal.IConventionAware;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultConventionsToPropertiesMapping {

    public final static Map TEST = GUtil.map(
            "testClassesDir", new ConventionValue() {
                public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                    return convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getClassesDir();
                }
            },
            "classpath", new ConventionValue() {
                public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                    return convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getRuntimeClasspath();
                }
            },
            "testResultsDir", new ConventionValue() {
                public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                    return convention.getPlugin(JavaPluginConvention.class).getTestResultsDir();
                }
            },
            "testReportDir", new ConventionValue() {
                public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                    return convention.getPlugin(JavaPluginConvention.class).getTestReportDir();
                }
            },
            "testSrcDirs", new ConventionValue() {
                public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                    Set<File> srcDirs = convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(
                            SourceSet.TEST_SOURCE_SET_NAME).getJava().getSrcDirs();
                    return new ArrayList<File>(srcDirs);
                }
            }
    );
}
