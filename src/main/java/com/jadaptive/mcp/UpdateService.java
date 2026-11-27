package com.jadaptive.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Periodically checks Athene for a newer Maverick SSH MCP build and caches the result. */
public final class UpdateService implements AutoCloseable {

    static final URI DEFAULT_UPDATE_URL = URI.create(
            "https://athene.jadaptive.com/t/files/jadaptive/maverick-ssh-mcp/current");

    private static final String CACHE_FILE = "maverick-ssh-mcp-available-version.json";
    private static final Duration CHECK_INTERVAL = Duration.ofDays(1);
    private static final Duration MAX_JITTER = Duration.ofHours(2);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI updateUrl;
    private final String currentVersion;
    private final Path cacheFile;
    private final Clock clock;
    private final HttpClient httpClient;
    private final Random random;
    private final AtomicReference<String> availableVersion = new AtomicReference<>();
    private final ScheduledExecutorService scheduler;

    public UpdateService(String currentVersion, Path dataDirectory) {
        this(DEFAULT_UPDATE_URL, currentVersion, dataDirectory, Clock.systemUTC(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), new Random());
    }

    UpdateService(URI updateUrl, String currentVersion, Path dataDirectory, Clock clock,
            HttpClient httpClient, Random random) {
        this.updateUrl = Objects.requireNonNull(updateUrl);
        this.currentVersion = Objects.requireNonNull(currentVersion);
        this.cacheFile = Objects.requireNonNull(dataDirectory).resolve(CACHE_FILE);
        this.clock = Objects.requireNonNull(clock);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.random = Objects.requireNonNull(random);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(UpdateService::newThread);
    }

    public String currentVersion() {
        return currentVersion;
    }

    public String availableVersion() {
        return availableVersion.get();
    }

    public void start() {
        start(true);
    }

    public void start(boolean updateChecksEnabled) {
        loadCachedVersion();
        if (updateChecksEnabled) {
            scheduler.execute(this::scheduleNextCheckFromCache);
        }
    }

    private void scheduleNextCheckFromCache() {
        long delayNanos = 0;
        try {
            if (Files.exists(cacheFile)) {
                Instant lastCheck = Files.getLastModifiedTime(cacheFile).toInstant();
                Instant nextCheck = lastCheck.plus(CHECK_INTERVAL).plus(jitter());
                delayNanos = Math.max(0, Duration.between(clock.instant(), nextCheck).toNanos());
            }
        }
        catch (Exception ex) {
            // A failed scheduling calculation is treated as an immediate update.
        }
        scheduler.schedule(this::checkForUpdate, delayNanos, TimeUnit.NANOSECONDS);
    }

    private void checkForUpdate() {
        try {
            HttpRequest request = HttpRequest.newBuilder(updateUrl)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return;
            }

            String version = versionFromJson(response.body());
            if (version == null || version.isBlank()) {
                return;
            }
            cacheVersion(version);
            availableVersion.set(version);
        }
        catch (Exception ex) {
            // Update checks are best-effort and must not disrupt MCP operation.
        }
        finally {
            if (!scheduler.isShutdown()) {
                scheduleNextCheckFromNow();
            }
        }
    }

    private void scheduleNextCheckFromNow() {
        Duration delay = CHECK_INTERVAL.plus(jitter());
        scheduler.schedule(this::checkForUpdate, delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    private Duration jitter() {
        long bound = MAX_JITTER.toNanos();
        return Duration.ofNanos(random.nextLong(bound * 2 + 1) - bound);
    }

    private String userAgent() {
        return "Maverick SSH MCP " + currentVersion
                + " (" + System.getProperty("os.name", "unknown")
                + "; " + System.getProperty("os.arch", "unknown")
                + "; " + System.getProperty("os.version", "unknown") + ")";
    }

    private void loadCachedVersion() {
        try {
            if (Files.isRegularFile(cacheFile)) {
                String version = versionFromJson(Files.readString(cacheFile));
                if (version != null && !version.isBlank()) {
                    availableVersion.set(version);
                }
            }
        }
        catch (Exception ex) {
            // Ignore corrupt or unreadable cache entries; the scheduled check will replace them.
        }
    }

    private void cacheVersion(String version) throws Exception {
        Files.createDirectories(cacheFile.getParent());
        Path temporary = Files.createTempFile(cacheFile.getParent(), CACHE_FILE, ".tmp");
        try {
            Files.writeString(temporary, JSON.writeValueAsString(java.util.Map.of("version", version)));
            try {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String versionFromJson(String json) throws Exception {
        JsonNode root = JSON.readTree(json);
        JsonNode version = root.path("version");
        return version.isTextual() ? version.textValue() : null;
    }

    private static Thread newThread(Runnable task) {
        Thread thread = new Thread(task, "maverick-mcp-update-check");
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}