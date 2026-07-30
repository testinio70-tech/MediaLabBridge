using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

var options = BridgeOptions.Parse(args);
var config = BridgeConfigStore.LoadOrCreate(options.Port, options.AllowExecution);
var jobs = new ConcurrentDictionary<string, JobEntry>(StringComparer.Ordinal);
var executionGate = new SemaphoreSlim(1, 1);

var builder = WebApplication.CreateBuilder(args);
builder.Logging.ClearProviders();
builder.Logging.AddSimpleConsole(settings =>
{
    settings.SingleLine = true;
    settings.TimestampFormat = "HH:mm:ss ";
});
builder.WebHost.UseUrls($"http://{options.BindAddress}:{options.Port}");
builder.WebHost.ConfigureKestrel(serverOptions =>
{
    serverOptions.Limits.MaxRequestBodySize = BridgeLimits.MaxRequestBodyBytes;
    serverOptions.Limits.KeepAliveTimeout = TimeSpan.FromMinutes(5);
    serverOptions.Limits.RequestHeadersTimeout = TimeSpan.FromSeconds(30);
});

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
    version = BridgeLimits.AppVersion,
    executionEnabled = options.AllowExecution,
    port = options.Port,
    maxTextLength = BridgeLimits.MaxTextLength,
    defaultTimeoutSeconds = BridgeLimits.DefaultExecutionTimeoutSeconds,
    maxTimeoutSeconds = BridgeLimits.MaxExecutionTimeoutSeconds,
    powerShell = PowerShellLocator.Describe()
}));

app.MapGet("/api/v1/jobs/{requestId}", async (string requestId) =>
{
    requestId = RequestTools.NormalizeRequestId(requestId);
    if (!jobs.TryGetValue(requestId, out var entry))
    {
        return Results.NotFound(new
        {
            ok = false,
            requestId,
            state = "not-found",
            error = "No se encontró ese trabajo."
        });
    }

    if (!entry.Task.IsCompleted)
    {
        return Results.Json(new
        {
            ok = true,
            requestId,
            state = "running",
            createdUtc = entry.CreatedUtc
        }, statusCode: StatusCodes.Status202Accepted);
    }

    var completed = await entry.Task;
    return Results.Json(completed.Body, statusCode: completed.StatusCode);
});

app.MapPost("/api/v1/command", async (CommandRequest request, ILogger<Program> logger) =>
{
    var requestId = RequestTools.NormalizeRequestId(request.RequestId);

    if (string.IsNullOrWhiteSpace(request.Text))
    {
        return Results.BadRequest(new { ok = false, requestId, error = "El texto está vacío." });
    }

    if (request.Text.Length > BridgeLimits.MaxTextLength)
    {
        return Results.BadRequest(new
        {
            ok = false,
            requestId,
            error = $"El texto supera el límite de {BridgeLimits.MaxTextLength:N0} caracteres."
        });
    }

    var action = request.Action?.Trim().ToLowerInvariant();
    if (action is not ("copy" or "execute"))
    {
        return Results.BadRequest(new
        {
            ok = false,
            requestId,
            error = "Acción no válida. Usa 'copy' o 'execute'."
        });
    }

    if (action == "execute" && !options.AllowExecution)
    {
        return Results.Json(new
        {
            ok = false,
            requestId,
            error = "La ejecución está desactivada. Inicia el receptor con --allow-execution para habilitarla de forma explícita."
        }, statusCode: StatusCodes.Status403Forbidden);
    }

    var timeoutSeconds = RequestTools.ResolveTimeoutSeconds(request.TimeoutSeconds);
    string? workingDirectory = null;
    if (action == "execute")
    {
        var directoryResult = RequestTools.ResolveWorkingDirectory(request.WorkingDirectory);
        if (!directoryResult.Success)
        {
            return Results.BadRequest(new
            {
                ok = false,
                requestId,
                error = directoryResult.Error
            });
        }
        workingDirectory = directoryResult.Path;
    }

    var payloadHash = RequestTools.ComputePayloadHash(action, request.Text, timeoutSeconds, workingDirectory);
    var newEntry = new JobEntry(
        payloadHash,
        DateTimeOffset.UtcNow,
        () => ProcessCommandAsync(
            requestId,
            action,
            request.Text,
            timeoutSeconds,
            workingDirectory,
            executionGate,
            logger));

    var entry = jobs.GetOrAdd(requestId, newEntry);
    if (!string.Equals(entry.PayloadHash, payloadHash, StringComparison.Ordinal))
    {
        return Results.Json(new
        {
            ok = false,
            requestId,
            error = "El identificador de solicitud ya existe con un contenido diferente."
        }, statusCode: StatusCodes.Status409Conflict);
    }

    if (ReferenceEquals(entry, newEntry))
    {
        _ = RemoveCompletedJobLaterAsync(requestId, entry, jobs);
    }

    var completed = await entry.Task;
    return Results.Json(completed.Body, statusCode: completed.StatusCode);
});

app.Lifetime.ApplicationStarted.Register(() =>
{
    Console.WriteLine();
    Console.WriteLine("============================================================");
    Console.WriteLine($" MediaLabBridge Desktop {BridgeLimits.AppVersion}");
    Console.WriteLine("============================================================");
    Console.WriteLine($" Token: {config.Token}");
    Console.WriteLine($" Puerto: {options.Port}");
    Console.WriteLine($" Ejecución PowerShell: {(options.AllowExecution ? "HABILITADA" : "DESACTIVADA")}");
    Console.WriteLine($" Motor preferido: {PowerShellLocator.Describe()}");
    Console.WriteLine($" Capacidad máxima: {BridgeLimits.MaxTextLength:N0} caracteres por script");
    Console.WriteLine($" Tiempo máximo: {BridgeLimits.MaxExecutionTimeoutSeconds / 60} minutos");
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

static async Task<CachedResponse> ProcessCommandAsync(
    string requestId,
    string action,
    string text,
    int timeoutSeconds,
    string? workingDirectory,
    SemaphoreSlim executionGate,
    ILogger logger)
{
    logger.LogInformation(
        "Solicitud {RequestId}: acción={Action}, caracteres={Length}, timeout={TimeoutSeconds}s",
        requestId,
        action,
        text.Length,
        timeoutSeconds);

    if (action == "copy")
    {
        var result = await BridgeActions.CopyToClipboardAsync(text, CancellationToken.None);
        var body = new CommandResponse(
            result.Success,
            requestId,
            "copy",
            result.Success ? "completed" : "failed",
            result.Success ? "Texto copiado al portapapeles del PC." : null,
            result.Error,
            null,
            null,
            null,
            false,
            false,
            0,
            null,
            null,
            text.Length);
        return new CachedResponse(body, result.Success ? StatusCodes.Status200OK : StatusCodes.Status500InternalServerError);
    }

    await executionGate.WaitAsync();
    try
    {
        var stopwatch = Stopwatch.StartNew();
        var result = await BridgeActions.ExecutePowerShellAsync(
            text,
            TimeSpan.FromSeconds(timeoutSeconds),
            workingDirectory!,
            BridgeLimits.MaxOutputCharacters);
        stopwatch.Stop();

        var body = new CommandResponse(
            result.ExitCode == 0 && !result.TimedOut,
            requestId,
            "execute",
            result.TimedOut ? "timed-out" : "completed",
            null,
            result.TimedOut ? $"La ejecución superó {timeoutSeconds} segundos." : null,
            result.ExitCode,
            result.StandardOutput,
            result.StandardError,
            result.TimedOut,
            result.OutputTruncated,
            stopwatch.ElapsedMilliseconds,
            result.Engine,
            workingDirectory,
            text.Length);

        return new CachedResponse(
            body,
            result.TimedOut ? StatusCodes.Status408RequestTimeout : StatusCodes.Status200OK);
    }
    finally
    {
        executionGate.Release();
    }
}

static async Task RemoveCompletedJobLaterAsync(
    string requestId,
    JobEntry entry,
    ConcurrentDictionary<string, JobEntry> jobs)
{
    try
    {
        await entry.Task;
        await Task.Delay(TimeSpan.FromMinutes(30));
        RemoveIfCurrent(requestId, entry, jobs);
    }
    catch
    {
        RemoveIfCurrent(requestId, entry, jobs);
    }
}

static void RemoveIfCurrent(
    string requestId,
    JobEntry entry,
    ConcurrentDictionary<string, JobEntry> jobs)
{
    if (jobs.TryGetValue(requestId, out var current) && ReferenceEquals(current, entry))
    {
        jobs.TryRemove(requestId, out _);
    }
}
