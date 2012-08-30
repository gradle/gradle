package org.gradle.api.plugins

import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;

class DistPluginConvention {

	/**
	 * The name of the library.
	 */
	String distributionName

	/**
	 * <p>The specification of the contents of the distribution.</p>
	 * <p>
	 * Use this {@link org.gradle.api.file.CopySpec} to include extra files/resource in the library distribution.
	 * <pre autoTested=''>
	 * apply plugin: 'java-library'
	 *
	 * libraryDistribution.from("some/dir") {
	 *   include "*.txt"
	 * }
	 * </pre>
	 * <p>
	 * Note that the library plugin pre configures this spec to; include the contents of "{@code src/dist}",
	 * copy the built jar and its dependencies
	 * into the "{@code lib}" directory.
	 */
	CopySpec distribution
	
	final Project project

	DistPluginConvention(Project project) {
		this.project = project
		distribution = project.copySpec {}
	}
}
