package dev.kwlew.haven.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

final class ModrinthClient {

    // The immutable ID of khaven, so checks keep working if the project's slug changes.
    private static final String ENDPOINT = "https://api.modrinth.com/v2/project/UcDctwM0/version";
    static final String PROJECT_URL = "https://modrinth.com/plugin/khaven";
    private HttpClient client;

    Optional<Release> check(String installed, String minecraft) throws IOException, InterruptedException {
        // Created on the worker, only when checking is enabled.
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        }
        HttpRequest request = HttpRequest.newBuilder(versionsUri(minecraft))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "kwlew/Haven/" + installed + " (" + PROJECT_URL + ")")
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth returned HTTP " + response.statusCode());
        }
        return findUpdate(response.body(), installed, minecraft);
    }

    static URI versionsUri(String minecraft) {
        JsonArray versions = new JsonArray();
        versions.add(minecraft);
        return URI.create(ENDPOINT + "?include_changelog=false&loaders=%5B%22paper%22%5D&game_versions="
                + URLEncoder.encode(versions.toString(), StandardCharsets.UTF_8));
    }

    static Optional<Release> findUpdate(String json, String installed, String minecraft) {
        ReleaseVersion current = ReleaseVersion.parse(installed).orElseThrow(
                () -> new IllegalArgumentException("Unrecognized installed version: " + installed));
        JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
        Release newest = null;
        ReleaseVersion newestVersion = current;
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject version = element.getAsJsonObject();
            if (!"release".equals(string(version, "version_type"))
                    || !"listed".equals(string(version, "status"))
                    || !contains(version, "loaders", "paper")
                    || !contains(version, "game_versions", minecraft)) continue;
            String number = string(version, "version_number");
            String id = string(version, "id");
            Optional<ReleaseVersion> parsed = ReleaseVersion.parse(number);
            if (parsed.isEmpty() || parsed.get().prerelease() || !id.matches("[A-Za-z0-9]+")) continue;
            if (parsed.get().compareTo(newestVersion) > 0) {
                newestVersion = parsed.get();
                newest = new Release(number, PROJECT_URL + "/version/" + id);
            }
        }
        return Optional.ofNullable(newest);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static boolean contains(JsonObject object, String key, String expected) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) return false;
        for (JsonElement entry : value.getAsJsonArray()) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()
                    && expected.equals(entry.getAsString())) return true;
        }
        return false;
    }

    record Release(String version, String url) { }
}
