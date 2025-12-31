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
        assert args.length > 0 : "At least one property name must be provided\nUsage: groovy CheckRemoteProjectRef.groovy <property-name> [property-name ...]"
        
        Properties props = new Properties()
        new File("gradle.properties").withInputStream { props.load(it) }
        
        args.each { propertyName ->
            String propertyValue = props.getProperty(propertyName)
            assert propertyValue : "Property '$propertyName' not found in gradle.properties"
            validateRef(propertyName, propertyValue)
        }
    }
    
    static void validateRef(String propertyName, String propertyValue) {
        // Parse format: <repository-url>#<commit-sha>
        assert propertyValue.contains("#") : "$propertyName must be in format: <repository-url>#<commit-sha>, got: $propertyValue"
        
        int hashIndex = propertyValue.indexOf("#")
        String repoUrl = propertyValue.substring(0, hashIndex)
        String commitSha = propertyValue.substring(hashIndex + 1).trim()
        
        assert repoUrl && commitSha : "$propertyName must be in format: <repository-url>#<commit-sha>, got: $propertyValue"
        
        println("Checking if commit $commitSha is tagged in $repoUrl")
        
        // Get all tags from the remote repository
        ExecResult result = exec("git ls-remote --tags $repoUrl")
        
        assert result.returnCode == 0 : "Failed to list tags from $repoUrl: ${result.stderr}"
        
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
        
        assert !tags.isEmpty() : "Commit $commitSha in repository $repoUrl is not tagged. Please ensure the commit is tagged before using it in smoke tests."
        
        println("✓ Commit $commitSha is tagged: ${tags.join(", ")}")
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

