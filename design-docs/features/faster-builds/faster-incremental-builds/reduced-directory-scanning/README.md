
Incremental build unnecessarily scans directories multiple times, even when it is very unlikely that anything has changed.

## Potential improvements

- Reuse the result of directory scanning
- Don't scan input directory multiple times when executing a task
- Use a hash to short circuit loading task input/output snapshots into heap
