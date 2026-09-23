#!/usr/bin/env python3
"""Boot one Paper version, check Haven's registration, then stop the server."""

import os
import subprocess
import re
import sys
import threading


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: smoke-paper.py <paper-version> <java-version>", file=sys.stderr)
        return 2

    version, java_version = sys.argv[1:]
    command = [
        "bash", "gradlew", "--no-configuration-cache", "--console=plain",
        "runServer", f"-PpaperVersion={version}", f"-PpaperJavaVersion={java_version}",
    ]
    if os.environ.get("PAPER_JAVA_HOME"):
        command.insert(2, "-Dorg.gradle.java.installations.paths=" + os.environ["PAPER_JAVA_HOME"])
    process = subprocess.Popen(
        command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1,
    )
    timer = threading.Timer(300, process.kill)
    timer.start()
    output = []
    sent_commands = False
    try:
        for line in process.stdout:
            print(line, end="", flush=True)
            output.append(line)
            if "Done (" in line and "For help" in line and not sent_commands:
                process.stdin.write("help home\nhaven reload\nstop\n")
                process.stdin.flush()
                sent_commands = True
        result = process.wait()
    finally:
        timer.cancel()
        if process.poll() is None:
            process.kill()
            process.wait()

    log = re.sub(r"\x1b\[[0-9;]*m", "", "".join(output))
    required = (
        "Haven enabled!", "Usage: /home [name]",
        "Configuration reloaded.", "Successfully registered internal expansion: haven",
    )
    missing = [marker for marker in required if marker not in log]
    if result != 0 or missing:
        print(f"Smoke test failed for Paper {version}: exit={result}, missing={missing}", file=sys.stderr)
        return 1
    print(f"Smoke test passed for Paper {version} on Java {java_version}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
