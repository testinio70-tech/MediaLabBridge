using System.Diagnostics;
using System.Text;

internal sealed record ClipboardResult(bool Success, string? Error);
internal sealed record PowerShellResult(
    int ExitCode,
    string StandardOutput,
    string StandardError,
    bool TimedOut,
    bool OutputTruncated,
    string Engine);
internal sealed record LimitedText(string Text, bool Truncated);

internal static class BridgeActions
{
    public static async Task<ClipboardResult> CopyToClipboardAsync(string text, CancellationToken cancellationToken)
    {
        if (!OperatingSystem.IsWindows())
        {
            return new ClipboardResult(false, "El portapapeles solo está disponible en la compilación de Windows.");
        }

        try
        {
            using var process = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = "clip.exe",
                    UseShellExecute = false,
                    RedirectStandardInput = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                }
            };

            process.Start();
            await process.StandardInput.WriteAsync(text.AsMemory(), cancellationToken);
            process.StandardInput.Close();
            await process.WaitForExitAsync(cancellationToken);

            if (process.ExitCode != 0)
            {
                var error = await process.StandardError.ReadToEndAsync(cancellationToken);
                return new ClipboardResult(false, string.IsNullOrWhiteSpace(error) ? "clip.exe devolvió un error." : error);
            }

            return new ClipboardResult(true, null);
        }
        catch (Exception error) when (error is not OperationCanceledException)
        {
            return new ClipboardResult(false, error.Message);
        }
    }

    public static async Task<PowerShellResult> ExecutePowerShellAsync(
        string command,
        TimeSpan timeout,
        string workingDirectory,
        int maxOutputCharacters)
    {
        if (!OperatingSystem.IsWindows())
        {
            return new PowerShellResult(-1, string.Empty, "PowerShell solo se ejecuta en Windows.", false, false, "unavailable");
        }

        var jobsFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "MediaLabBridge",
            "Jobs");
        Directory.CreateDirectory(jobsFolder);
        var scriptPath = Path.Combine(jobsFolder, $"job-{Guid.NewGuid():N}.ps1");
        var encoding = new UTF8Encoding(encoderShouldEmitUTF8Identifier: true);
        await File.WriteAllTextAsync(scriptPath, command, encoding);

        var engine = PowerShellLocator.Find();
        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = engine.Path,
                WorkingDirectory = workingDirectory,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                RedirectStandardInput = false,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8,
                CreateNoWindow = true
            }
        };
        process.StartInfo.ArgumentList.Add("-NoLogo");
        process.StartInfo.ArgumentList.Add("-NoProfile");
        process.StartInfo.ArgumentList.Add("-NonInteractive");
        process.StartInfo.ArgumentList.Add("-ExecutionPolicy");
        process.StartInfo.ArgumentList.Add("Bypass");
        var escapedScriptPath = scriptPath.Replace("'", "''", StringComparison.Ordinal);
        var wrapper = "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); " +
                      "$OutputEncoding = [System.Text.UTF8Encoding]::new($false); " +
                      $"& '{escapedScriptPath}'; " +
                      "$bridgeSuccess = $?; " +
                      "if (-not $bridgeSuccess) { exit 1 }";
        process.StartInfo.ArgumentList.Add("-Command");
        process.StartInfo.ArgumentList.Add(wrapper);

        try
        {
            process.Start();
            var stdoutTask = ReadLimitedAsync(process.StandardOutput, maxOutputCharacters);
            var stderrTask = ReadLimitedAsync(process.StandardError, maxOutputCharacters);

            using var timeoutSource = new CancellationTokenSource(timeout);
            try
            {
                await process.WaitForExitAsync(timeoutSource.Token);
                var stdout = await stdoutTask;
                var stderr = await stderrTask;
                return new PowerShellResult(
                    process.ExitCode,
                    stdout.Text,
                    stderr.Text,
                    false,
                    stdout.Truncated || stderr.Truncated,
                    engine.Name);
            }
            catch (OperationCanceledException)
            {
                TryKillProcessTree(process);
                try
                {
                    await process.WaitForExitAsync();
                }
                catch (InvalidOperationException)
                {
                    // The process already stopped.
                }

                var stdout = await stdoutTask;
                var stderr = await stderrTask;
                return new PowerShellResult(
                    -1,
                    stdout.Text,
                    stderr.Text,
                    true,
                    stdout.Truncated || stderr.Truncated,
                    engine.Name);
            }
        }
        catch (Exception error)
        {
            return new PowerShellResult(-1, string.Empty, error.Message, false, false, engine.Name);
        }
        finally
        {
            try
            {
                File.Delete(scriptPath);
            }
            catch (IOException)
            {
                // A locked temporary script is cleaned on a later run.
            }
            catch (UnauthorizedAccessException)
            {
                // Keep the result even if cleanup is temporarily blocked.
            }
        }
    }

    private static async Task<LimitedText> ReadLimitedAsync(StreamReader reader, int maxCharacters)
    {
        var builder = new StringBuilder(Math.Min(maxCharacters, 64 * 1024));
        var buffer = new char[16 * 1024];
        var truncated = false;

        while (true)
        {
            var read = await reader.ReadAsync(buffer.AsMemory());
            if (read == 0)
            {
                break;
            }

            var remaining = maxCharacters - builder.Length;
            if (remaining > 0)
            {
                builder.Append(buffer, 0, Math.Min(read, remaining));
            }
            if (read > remaining)
            {
                truncated = true;
            }
        }

        if (truncated)
        {
            builder.AppendLine();
            builder.Append("[Salida truncada por MediaLabBridge]");
        }
        return new LimitedText(builder.ToString(), truncated);
    }

    private static void TryKillProcessTree(Process process)
    {
        try
        {
            process.Kill(entireProcessTree: true);
        }
        catch (InvalidOperationException)
        {
            // Process already stopped.
        }
        catch (System.ComponentModel.Win32Exception)
        {
            // Windows already released the process.
        }
    }
}

internal sealed record PowerShellEngine(string Path, string Name);

internal static class PowerShellLocator
{
    public static PowerShellEngine Find()
    {
        var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        var candidates = new[]
        {
            Path.Combine(programFiles, "PowerShell", "7", "pwsh.exe"),
            FindOnPath("pwsh.exe"),
            FindOnPath("powershell.exe")
        };

        foreach (var candidate in candidates)
        {
            if (!string.IsNullOrWhiteSpace(candidate) && File.Exists(candidate))
            {
                return new PowerShellEngine(
                    candidate,
                    string.Equals(Path.GetFileName(candidate), "pwsh.exe", StringComparison.OrdinalIgnoreCase)
                        ? "PowerShell 7"
                        : "Windows PowerShell");
            }
        }

        return new PowerShellEngine("powershell.exe", "Windows PowerShell");
    }

    public static string Describe() => OperatingSystem.IsWindows() ? Find().Name : "PowerShell (Windows)";

    private static string? FindOnPath(string executable)
    {
        var path = Environment.GetEnvironmentVariable("PATH");
        if (string.IsNullOrWhiteSpace(path))
        {
            return null;
        }

        foreach (var folder in path.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            try
            {
                var candidate = Path.Combine(folder.Trim('"'), executable);
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }
            catch (ArgumentException)
            {
                // Ignore malformed PATH entries.
            }
        }
        return null;
    }
}
