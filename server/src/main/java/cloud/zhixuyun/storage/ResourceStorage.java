package cloud.zhixuyun.storage;

import java.io.IOException;

public interface ResourceStorage {
    StoredResource store(String namespace, String filename, String contentType, byte[] content) throws IOException;

    byte[] read(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;

    record StoredResource(String storageBackend, String storageKey, long fileSize) {}
}
