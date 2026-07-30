using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

const int MaxTextLength = 100_000;
const int ExecutionTimeoutSeconds = 60;

var options = BridgeOptions.Parse(args);
var config = BridgeConfigStore.LoadOrCreate(options.Port, options.AllowExecution);

var builder = WebApplication.CreateBuilder(args);
builder.Logging.ClearProviders();
builder.Logging.AddSimpleConsole(settings =>
{
    settings.SingleLine = true;
    settings.TimestampFormat = "HH:mm:ss ";
});
builder.WebHost.UseUrls($"http://{options.BindAddress}:{options.Port}");

var app = builder.Build();

app.Use(async (context, next) =>
{
    if (context.Request.Path.StartsWithSegments("/api"))
    {
        var authorization = context.Request.Headers.Authorization.ToString();
        var suppliedToken = authorization.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)
            ? authorization[7..].Trim()
            : string.Empty;

        if (!TokenTools.FixedTimeEquals(config.Token, suppliedToken))
        {
            context.Response.StatusCode = StatusCodes.Status401Unauthorized;
            await context.Response.WriteAsJsonAsync(new
            {
                ok = false,
                error = "Token inválido o ausente."
            });
            return;
        }
    }

    await next();
});

app.MapGet("/health", () => Results.Json(new
{
    status = "ok",
    version = "0.1.0",
    executionEnabled = options.AllowExecution,
    port = options.Port
}));

app.MapPost("/api/v1/command", async (CommandRequest request, ILogger<Program> logger, CancellationToken cancellationToken) =>
{
    var requestId = string.IsNullOrWhiteSpace(request.RequestId)
        ? Guid.NewGuid().ToString()
        : request.RequestId.Trim();

    if (string.IsNullOrWhiteSpace(request.Text))
    {
        return Results.BadRequest(new { ok = false, requestId, error = "El texto está vacío." });
    }

    if (request.Text.Length > MaxTextLength)
    {
        return Results.BadRequest(new
        {
            ok = false,
            requestId,
            error = $"El texto supera el límite de {MaxTextLength} caracteres."
        });
    }

    logger.LogInformation("Solicitud {RequestId}: acción={Action}, origen={RemoteIp}",
        requestId,
        request.Action,
        "red-local");

    switch (request.Action?.Trim().ToLowerInvariant())
    {
        case "copy":
        {
            var result = await BridgeActions.CopyToClipboardAsync(request.Text, cancellationToken);
            return result.Success
                ? Results.Ok(new { ok = true, requestId, action = "copy", message = "Texto copiado al portapapeles del PC." })
                : Results.Json(new { ok = false, requestId, error = result.Error }, statusCode: 500);
        }

        case "execute":
        {
            if (!options.AllowExecution)
            {
                return Results.Json(new
                {
                    ok = false,
                    requestId,
                    error = "La ejecución está desactivada. Inicia el receptor con --allow-execution para habilitarla de forma explícita."
                }, statusCode: 403);
            }

            var result = await BridgeActions.ExecutePowerShellAsync(
                request.Text,
                TimeSpan.FromSeconds(ExecutionTimeoutSeconds),
                cancellationToken);

            return Results.Json(new
            {
                ok = result.ExitCode == 0,
                requestId,
                action = "execute",
                exitCode = result.ExitCode,
                stdout = result.StandardOutput,
                stderr = result.StandardError,
                timedOut = result.TimedOut
            }, statusCode: result.TimedOut ? 408 : 200);
        }

        default:
            return Results.BadRequest(new
            {
                ok = false,
                requestId,
                error = "Acción no válida. Usa 'copy' o 'execute'."
            });
    }
});

app.Lifetime.ApplicationStarted.Register(() =>
{
    Console.WriteLine();
    Console.WriteLine("============================================================");
    Console.WriteLine(" MediaLabBridge Desktop 0.1.0");
    Console.WriteLine("============================================================");
    Console.WriteLine($" Token: {config.Token}");
    Console.WriteLine($" Puerto: {options.Port}");
    Console.WriteLine($" Ejecución PowerShell: {(options.AllowExecution ? "HABILITADA" : "DESACTIVADA")}");
    Console.WriteLine(" Direcciones sugeridas para la APK:");
    foreach (var address in NetworkTools.GetLocalIpv4Addresses())
    {
        Console.WriteLine($"   {address}:{options.Port}");
    }
    Console.WriteLine();
    Console.WriteLine("Mantén esta ventana abierta mientras uses la aplicación.");
    Console.WriteLine("Permite el acceso únicamente en redes privadas cuando Windows lo solicite.");
    Console.WriteLine("============================================================");
    Console.WriteLine();
});

await app.RunAsync();

internal sealed record CommandRequest(string? RequestId, string? Action, string Text);

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

internal sealed record ClipboardResult(bool Success, string? Error);
internal sealed record PowerShellResult(int ExitCode, string StandardOutput, string StandardError, bool TimedOut);

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
        CancellationToken cancellationToken)
    {
        if (!OperatingSystem.IsWindows())
        {
            return new PowerShellResult(-1, string.Empty, "PowerShell solo se ejecuta en Windows.", false);
        }

        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = "powershell.exe",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                RedirectStandardInput = false,
                CreateNoWindow = true
            }
        };
        process.StartInfo.ArgumentList.Add("-NoLogo");
        process.StartInfo.ArgumentList.Add("-NoProfile");
        process.StartInfo.ArgumentList.Add("-NonInteractive");
        process.StartInfo.ArgumentList.Add("-Command");
        process.StartInfo.ArgumentList.Add(command);

        process.Start();
        var stdoutTask = process.StandardOutput.ReadToEndAsync(cancellationToken);
        var stderrTask = process.StandardError.ReadToEndAsync(cancellationToken);

        using var timeoutSource = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeoutSource.CancelAfter(timeout);

        try
        {
            await process.WaitForExitAsync(timeoutSource.Token);
            return new PowerShellResult(
                process.ExitCode,
                await stdoutTask,
                await stderrTask,
                false);
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                process.Kill(entireProcessTree: true);
            }
            catch (InvalidOperationException)
            {
                // Process already stopped.
            }

            return new PowerShellResult(
                -1,
                await stdoutTask,
                await stderrTask,
                true);
        }
    }
}
