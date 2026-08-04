package cloud.zhixuyun.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class LocalResourceStorage implements ResourceStorage {
    private final Path baseDir;

    public LocalResourceStorage(@Value("${zhixuyun.resource-storage.base-dir:./.local/resource-files}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredResource store(String namespace, String filename, String contentType, byte[] content) throws IOException {
        String extension = extensionOf(filename);
        LocalDate today = LocalDate.now();
        Path directory = baseDir.resolve(namespace).resolve(String.valueOf(today.getYear()))
                .resolve(String.format("%02d", today.getMonthValue()));
        Files.createDirectories(directory);
        String key = namespace + "/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + UUID.randomUUID() + extension;
        Path target = baseDir.resolve(key).normalize();
        ensureWithinBaseDir(target);
        Files.write(target, content);
        return new StoredResource("LOCAL_FS", key, content.length);
    }

    @Override
    public byte[] read(String storageKey) throws IOException {
        Path target = resolve(storageKey);
        return Files.readAllBytes(target);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path target = resolve(storageKey);
        Files.deleteIfExists(target);
    }

    private Path resolve(String storageKey) {
        Path target = baseDir.resolve(storageKey).normalize();
        ensureWithinBaseDir(target);
        return target;
    }

    private void ensureWithinBaseDir(Path target) {
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("Storage path escaped base directory");
        }
    }

    private static String extensionOf(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index);
    }
}
