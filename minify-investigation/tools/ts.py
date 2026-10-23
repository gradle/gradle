import sys, time
t0 = time.monotonic()
for line in sys.stdin:
    sys.stdout.write(f"{time.monotonic()-t0:7.2f} {line}"); sys.stdout.flush()
