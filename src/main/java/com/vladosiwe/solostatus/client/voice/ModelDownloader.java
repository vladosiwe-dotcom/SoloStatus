package com.vladosiwe.solostatus.client.voice;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Загрузка и распаковка модели Vosk. Запускается только явной командой игрока (/systemvoice download).
 * Не зависит от классов Minecraft.
 */
public final class ModelDownloader {
    public record ModelInfo(String language, String name, String url, int sizeMb) {}

    public static final ModelInfo RU = new ModelInfo("ru", "vosk-model-small-ru-0.22",
            "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", 45);
    public static final ModelInfo EN = new ModelInfo("en", "vosk-model-small-en-us-0.15",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", 40);

    private ModelDownloader() {}

    public static ModelInfo forLanguage(String language) {
        return "en".equals(language) ? EN : RU;
    }

    /** Похоже ли содержимое папки на модель Vosk. */
    public static boolean isModel(Path dir) {
        return dir != null && Files.isDirectory(dir) && (Files.isDirectory(dir.resolve("am")) || Files.exists(dir.resolve("conf/model.conf"))
                || Files.exists(dir.resolve("final.mdl")));
    }

    /**
     * Скачивает zip модели в baseDir, распаковывает и удаляет архив.
     * progress получает проценты 0..100 (или -1, если размер неизвестен). Возвращает папку модели.
     */
    public static Path download(ModelInfo info, Path baseDir, IntConsumer progress) throws IOException, InterruptedException {
        Files.createDirectories(baseDir);
        Path zip = baseDir.resolve(info.name() + ".zip.part");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(info.url()))
                .header("User-Agent", "SoloStatusSystem-Mod")
                .GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(zip)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int lastPct = -2;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                int pct = total > 0 ? (int) (done * 100 / total) : -1;
                if (pct != lastPct && (pct < 0 || pct % 5 == 0)) {
                    progress.accept(pct);
                    lastPct = pct;
                }
            }
        }
        unzip(zip, baseDir);
        Files.deleteIfExists(zip);
        Path dir = baseDir.resolve(info.name());
        if (!isModel(dir)) throw new IOException("Архив не содержит модель " + info.name());
        return dir;
    }

    /** Распаковка с защитой от выхода за пределы папки (zip slip). */
    static void unzip(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                Path out = root.resolve(e.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("Недопустимый путь в архиве: " + e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Первая подходящая модель в папке (если игрок распаковал её вручную под другим именем). */
    public static Path findAnyModel(Path baseDir) {
        if (!Files.isDirectory(baseDir)) return null;
        try (Stream<Path> s = Files.list(baseDir)) {
            return s.filter(ModelDownloader::isModel).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
