package org.gradle.api.plugins

/**
 * Extension for {@link JavaLibraryPlugin }
 * 
 *  
 * <p>The extension for the java library plugin.</p>
 * <p>
 * Use this class to configure the name of the distribution.
 * <pre autoTested=''>
 * apply plugin: 'java-library'
 *
 * distribution {
 *   name = 'my-name'
 * }
 * </pre>
 * <p>
 * @author scogneau
 *
 */
class DistributionExtension {

	/**
	 * The name of the distribution
	 */
	String name;
}
