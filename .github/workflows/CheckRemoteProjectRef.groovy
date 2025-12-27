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

/**
 * Validates that remote project refs specified in gradle.properties are tagged.
 * 
 * This script reads properties from gradle.properties in the format:
 * <property-name>=<repository-url>#<commit-sha>
 * 
 * For each property, it uses git ls-remote to check if the commit is tagged.
 * 
 * Usage: groovy CheckRemoteProjectRef.groovy <property-name> [property-name ...]
 */
class CheckRemoteProjectRef {
    
    static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Error: At least one property name must be provided")
            System.err.println("Usage: groovy CheckRemoteProjectRef.groovy <property-name> [property-name ...]")
            System.exit(1)
        }
        
        List<String> propertiesToCheck = args.toList()
        
        File gradleProperties = new File("gradle.properties")
        if (!gradleProperties.exists()) {
            System.err.println("Error: gradle.properties not found")
            System.exit(1)
        }
        
        Properties props = new Properties()
        gradleProperties.withInputStream { props.load(it) }
        
        boolean allValid = true
        
        propertiesToCheck.each { propertyName ->
            String propertyValue = props.getProperty(propertyName)
            if (!propertyValue) {
                System.err.println("Error: Property '$propertyName' not found in gradle.properties")
                allValid = false
                return
            }
            
            if (!validateRef(propertyName, propertyValue)) {
                allValid = false
            }
        }
        
        if (!allValid) {
            System.exit(1)
        }
    }
    
    static boolean validateRef(String propertyName, String propertyValue) {
        // Parse format: <repository-url>#<commit-sha>
        if (!propertyValue.contains("#")) {
            System.err.println("Error: $propertyName must be in format: <repository-url>#<commit-sha>, got: $propertyValue")
            return false
        }
        
        int hashIndex = propertyValue.indexOf("#")
        String repoUrl = propertyValue.substring(0, hashIndex)
        String commitSha = propertyValue.substring(hashIndex + 1).trim()
        
        if (!repoUrl || !commitSha) {
            System.err.println("Error: $propertyName must be in format: <repository-url>#<commit-sha>, got: $propertyValue")
            return false
        }
        
        // Skip validation for local repositories (file:// paths or paths without ://)
        if (repoUrl.startsWith("file://") || !repoUrl.contains("://")) {
            println("Skipping tag validation for local repository: $repoUrl")
            return true
        }
        
        println("Checking if commit $commitSha is tagged in $repoUrl")
        
        // Get all tags from the remote repository
        ExecResult result = exec("git ls-remote --tags $repoUrl")
        
        if (result.returnCode != 0) {
            System.err.println("Error: Failed to list tags from $repoUrl")
            System.err.println(result.stderr)
            return false
        }
        
        // Parse ls-remote output: format is "<commit-sha>\trefs/tags/<tag-name>" or "<commit-sha>\trefs/tags/<tag-name>^{}"
        List<String> tags = []
        result.stdout.eachLine { line ->
            if (!line.trim()) {
                return
            }
            
            String[] parts = line.split("\t")
            if (parts.length == 2) {
                String tagCommitSha = parts[0].trim()
                String tagRef = parts[1].trim()
                
                // Check if this tag points to our commit (handle both direct tags and annotated tag dereferencing)
                if (tagCommitSha == commitSha || tagCommitSha.startsWith(commitSha)) {
                    // Extract tag name from refs/tags/<tag-name> or refs/tags/<tag-name>^{}
                    String tagName = tagRef.replaceFirst("^refs/tags/", "").replaceFirst("\\^\\{\\}\$", "")
                    tags.add(tagName)
                }
            }
        }
        
        if (tags.isEmpty()) {
            System.err.println("Error: Commit $commitSha in repository $repoUrl is not tagged.")
            System.err.println("Please ensure the commit is tagged before using it in smoke tests.")
            return false
        }
        
        println("✓ Commit $commitSha is tagged: ${tags.join(", ")}")
        return true
    }
    
    @groovy.transform.ToString
    static class ExecResult {
        String stdout
        String stderr
        int returnCode
    }
    
    static ExecResult exec(String command) {
        Process process = command.execute()
        StringBuffer stdout = new StringBuffer()
        StringBuffer stderr = new StringBuffer()
        
        process.consumeProcessOutput(stdout, stderr)
        int returnCode = process.waitFor()
        
        return new ExecResult(stdout: stdout.toString(), stderr: stderr.toString(), returnCode: returnCode)
    }
}

