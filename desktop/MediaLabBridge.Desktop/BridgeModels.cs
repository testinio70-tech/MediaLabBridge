using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

internal static class BridgeLimits
{
    public const string AppVersion = "0.3.0";
    public const int MaxTextLength = 2_000_000;
    public const long MaxRequestBodyBytes = 8L * 1024 * 1024;
    public const int DefaultExecutionTimeoutSeconds = 15 * 60;
    public const int MaxExecutionTimeoutSeconds = 30 * 60;
    public const int MaxOutputCharacters = 2_000_000;
}

internal sealed record CommandRequest(
    string? RequestId,
    string? Action,
    string Text,
    int? TimeoutSeconds,
    string? WorkingDirectory);

internal sealed record CommandResponse(
    bool Ok,
    string RequestId,
    string Action,
    string State,
    string? Message,
    string? Error,
    int? ExitCode,
    string? Stdout,
    string? Stderr,
    bool TimedOut,
    bool OutputTruncated,
    long DurationMs,
    string? PowerShell,
    string? WorkingDirectory,
    int TextLength);

internal sealed record CachedResponse(CommandResponse Body, int StatusCode);

internal sealed class JobEntry
{
    private readonly Lazy<Task<CachedResponse>> work;

    public JobEntry(string payloadHash, DateTimeOffset createdUtc, Func<Task<CachedResponse>> factory)
    {
        PayloadHash = payloadHash;
        CreatedUtc = createdUtc;
        work = new Lazy<Task<CachedResponse>>(factory, LazyThreadSafetyMode.ExecutionAndPublication);
    }

    public string PayloadHash { get; }
    public DateTimeOffset CreatedUtc { get; }
    public Task<CachedResponse> Task => work.Value;
}

internal sealed record BridgeOptions(string BindAddress, int Port, bool AllowExecution)
{
    public static BridgeOptions Parse(string[] args)
    {
        var bind = "0.0.0.0";
        var port = 8765;
        var allowExecution = false;

        for (var index = 0; index < args.Length; index++)
        {
            switch (args[index])
            {
                case "--bind" when index + 1 < args.Length:
                    bind = args[++index];
                    break;
                case "--port" when index + 1 < args.Length && int.TryParse(args[++index], out var parsedPort):
                    port = parsedPort;
                    break;
                case "--allow-execution":
                    allowExecution = true;
                    break;
                case "--help":
                case "-h":
                    Console.WriteLine("MediaLabBridge.Desktop [--bind 0.0.0.0] [--port 8765] [--allow-execution]");
                    Environment.Exit(0);
                    break;
            }
        }

        if (port is < 1 or > 65535)
        {
            throw new ArgumentOutOfRangeException(nameof(port), "El puerto debe estar entre 1 y 65535.");
        }

        return new BridgeOptions(bind, port, allowExecution);
    }
}

internal sealed record BridgeConfig(string Token, int Port, bool ExecutionEnabled, DateTimeOffset CreatedUtc);

internal static class BridgeConfigStore
{
    public static BridgeConfig LoadOrCreate(int port, bool executionEnabled)
    {
        var folder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "MediaLabBridge");
        Directory.CreateDirectory(folder);

        var path = Path.Combine(folder, "bridge-config.json");
        if (File.Exists(path))
        {
            try
            {
                var existing = JsonSerializer.Deserialize<BridgeConfig>(File.ReadAllText(path));
                if (existing is not null && !string.IsNullOrWhiteSpace(existing.Token))
                {
                    var updated = existing with { Port = port, ExecutionEnabled = executionEnabled };
                    Save(path, updated);
                    return updated;
                }
            }
            catch (JsonException)
            {
                // A damaged local config is replaced with a fresh token.
            }
        }

        var created = new BridgeConfig(
            TokenTools.GenerateToken(),
            port,
            executionEnabled,
            DateTimeOffset.UtcNow);
        Save(path, created);
        return created;
    }

    private static void Save(string path, BridgeConfig config)
    {
        var json = JsonSerializer.Serialize(config, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(path, json);
    }
}

internal static class TokenTools
{
    public static string GenerateToken() => Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant();

    public static bool FixedTimeEquals(string expected, string supplied)
    {
        var expectedBytes = Encoding.UTF8.GetBytes(expected);
        var suppliedBytes = Encoding.UTF8.GetBytes(supplied);
        return expectedBytes.Length == suppliedBytes.Length &&
               CryptographicOperations.FixedTimeEquals(expectedBytes, suppliedBytes);
    }
}

internal static class RequestTools
{
    public static string NormalizeRequestId(string? value)
    {
        var result = string.IsNullOrWhiteSpace(value) ? Guid.NewGuid().ToString() : value.Trim();
        return result.Length <= 128 ? result : result[..128];
    }

    public static int ResolveTimeoutSeconds(int? requested)
    {
        if (requested is null)
        {
            return BridgeLimits.DefaultExecutionTimeoutSeconds;
        }

        return Math.Clamp(requested.Value, 5, BridgeLimits.MaxExecutionTimeoutSeconds);
    }

    public static WorkingDirectoryResult ResolveWorkingDirectory(string? rawValue)
    {
        var value = string.IsNullOrWhiteSpace(rawValue)
            ? Environment.GetFolderPath(Environment.SpecialFolder.UserProfile)
            : Environment.ExpandEnvironmentVariables(rawValue.Trim().Trim('"'));

        try
        {
            var fullPath = Path.GetFullPath(value);
            return Directory.Exists(fullPath)
                ? new WorkingDirectoryResult(true, fullPath, null)
                : new WorkingDirectoryResult(false, null, $"La carpeta de trabajo no existe: {fullPath}");
        }
        catch (Exception error) when (error is ArgumentException or NotSupportedException or PathTooLongException)
        {
            return new WorkingDirectoryResult(false, null, $"Carpeta de trabajo no válida: {error.Message}");
        }
    }

    public static string ComputePayloadHash(string action, string text, int timeoutSeconds, string? workingDirectory)
    {
        var payload = $"{action}\n{timeoutSeconds}\n{workingDirectory}\n{text}";
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(payload)));
    }
}

internal sealed record WorkingDirectoryResult(bool Success, string? Path, string? Error);

internal static class NetworkTools
{
    public static IReadOnlyList<string> GetLocalIpv4Addresses()
    {
        try
        {
            return Dns.GetHostEntry(Dns.GetHostName())
                .AddressList
                .Where(address => address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(address))
                .Select(address => address.ToString())
                .Distinct()
                .OrderBy(address => address)
                .ToArray();
        }
        catch (SocketException)
        {
            return Array.Empty<string>();
        }
    }
}
