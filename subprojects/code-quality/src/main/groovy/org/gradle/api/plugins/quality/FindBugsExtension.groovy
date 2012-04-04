/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

/**
 * Configuration options for the FindBugs plugin.
 *
 * @see FindBugsPlugin
 */
class FindBugsExtension extends CodeQualityExtension {
   /**
    * Set the analysis effort level. The value specified should be one of min, default, or max. See
    * <a href="http://findbugs.sourceforge.net/manual/running.html#commandLineOptions">Chapter 4, Section 3 "Command-line Options"</a>
    * for more information about setting the analysis level.
    */
   String effort

   /**
    * An optional attribute. It specifies the priority threshold for reporting bugs. If set to "low", all bugs are reported. If set to
    * "medium" (the default), medium and high priority bugs are reported. If set to "high", only high priority bugs are reported.
    */
   String reportLevel

   /**
    * Optional attribute. It specifies a comma-separated list of bug detectors which should be run. The bug detectors are specified by
    * their class names, without any package qualification. By default, all detectors which are not disabled by default are run.
    */
   // TODO Add DSL methods to building up visitors list
   Collection<String> visitors

   /**
    * Optional attribute. It is like the visitors attribute, except it specifies detectors which will not be run.
    */
   // TODO Add DSL methods to building up omitVisitors list
   Collection<String> omitVisitors

   /**
    * Optional attribute. It specifies the filename of a filter specifying bugs to exclude from being reported. See
    * <a href="http://findbugs.sourceforge.net/manual/filter.html">Chapter 8, Filter Files</a>. This is not the same as the
    * exclude parameter, which influences the source parameter.
    */
   File excludeFilter

   /**
    * Optional attribute. It specifies the filename of a filter specifying which bugs are reported. See
    * <a href="http://findbugs.sourceforge.net/manual/filter.html">Chapter 8, Filter Files</a>. This is not the same as the
    * include parameter, which influences the source parameter.
    */
   File includeFilter
}
