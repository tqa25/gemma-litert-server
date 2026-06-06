package dev.gemma.androidbackend;

final class AutomationShellResult {
  final int exitCode;
  final byte[] stdout;
  final String stderr;
  final long durationMs;

  AutomationShellResult(int exitCode, byte[] stdout, String stderr, long durationMs) {
    this.exitCode = exitCode;
    this.stdout = stdout;
    this.stderr = stderr == null ? "" : stderr;
    this.durationMs = durationMs;
  }

  String stdoutUtf8() {
    return new String(stdout, java.nio.charset.StandardCharsets.UTF_8);
  }
}
