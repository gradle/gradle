
- Fix lifecycle and diagnostic issues that prevent the daemon to be enabled by default
    - Clean up cached `ClassLoaders` that cannot be used again.
- Fix Windows specific blockers
- Switch on by default
    - Documentation
    - Adjust test suites and fixtures for this.
    
### Daemon robustness    

- All client and daemon reads on the connection should have a timeout.
- Daemon should exit when its entry is removed from the registry.
    