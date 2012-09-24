package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DistZipIntegrationTest extends AbstractIntegrationSpec {

	def canCreateADistributionWithSrcDistRuntime() {
		given:
		createDir('libs') {
			file 'a.jar'
		}
		createDir('src/dist') {
			file 'file1.txt'
			dir2 {
				file 'file2.txt'
			}
		}
		and:
		buildFile << """
		apply plugin:'java-library'
            distribution{
				name ='SuperApp'
			}

			dependencies {
				runtime files('libs/a.jar')
			}
        """
		when:
		run 'distZip'
		then:
		def expandDir = file('expanded')
		file('build/distributions/SuperApp.zip').unzipTo(expandDir)
		expandDir.assertHasDescendants('lib/a.jar', 'dist/file1.txt', 'dist/dir2/file2.txt','canCreateADistributionWithSrcDistRuntime.jar')
	}
	
	def canCreateADistributionIncludingOtherFile() {
		given:
		createDir('libs') {
			file 'a.jar'
		}
		createDir('src/dist') {
			file 'file1.txt'
			dir2 {
				file 'file2.txt'
			}
		}
		createDir('other'){
			file 'file3.txt'
		}
		createDir('other2'){
			file 'file4.txt'	
		}
		and:
		buildFile << """
		apply plugin:'java-library'
            distribution{
				name ='SuperApp'
			}

			dependencies {
				runtime files('libs/a.jar')
			}

			distZip{
				from('other')
				from('other2'){
					into('other2')
				}
			}
        """
		when:
		run 'distZip'
		then:
		def expandDir = file('expanded')
		file('build/distributions/SuperApp.zip').unzipTo(expandDir)
		expandDir.assertHasDescendants('lib/a.jar', 'file1.txt', 'dir2/file2.txt','canCreateADistributionIncludingOtherFile.jar','file3.txt','other2/file4.txt')
	}
}
