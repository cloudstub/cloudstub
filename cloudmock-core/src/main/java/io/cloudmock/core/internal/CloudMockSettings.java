package io.cloudmock.core.internal;

import io.cloudmock.core.StatePersistence;
import io.cloudmock.core.spi.CloudMockService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Mutable holder for all pre-start configuration of a {@link io.cloudmock.core.CloudMock} instance.
 *
 * <p>Populated by CloudMock's fluent {@code withX} methods and then consumed (read-only) by the
 * factories and initialisers that build the running engine.
 */
public final class CloudMockSettings {

    private int port = 0;                  // 0 = dynamic port
    private int maxRequestHistory;         // <= 0 = unbounded
    private Set<String> enabledServiceIds; // null = register every discovered module
    private Path storeDirectory;           // null = in-memory store
    private StatePersistence persistenceBackend = StatePersistence.APPEND_LOG;
    private final List<CloudMockService> explicitServices = new ArrayList<>();

    public CloudMockSettings(int defaultMaxRequestHistory) {
        this.maxRequestHistory = defaultMaxRequestHistory;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setMaxRequestHistory(int maxRequestHistory) {
        this.maxRequestHistory = maxRequestHistory;
    }

    public void setEnabledServiceIds(Collection<String> serviceIds) {
        this.enabledServiceIds = (serviceIds == null) ? null : Set.copyOf(serviceIds);
    }

    public void setStoreDirectory(Path storeDirectory) {
        this.storeDirectory = storeDirectory;
    }

    public void setPersistenceBackend(StatePersistence persistenceBackend) {
        this.persistenceBackend = persistenceBackend;
    }

    public void addExplicitService(CloudMockService service) {
        this.explicitServices.add(service);
    }

    public int port() {
        return port;
    }

    public int maxRequestHistory() {
        return maxRequestHistory;
    }

    /** @return the set of enabled service IDs, or {@code null} to register every discovered module. */
    public Set<String> enabledServiceIds() {
        return enabledServiceIds;
    }

    /** @return the persistent store directory, or {@code null} for an in-memory store. */
    public Path storeDirectory() {
        return storeDirectory;
    }

    /** @return the persistent backend to use when {@link #storeDirectory()} is set. */
    public StatePersistence persistenceBackend() {
        return persistenceBackend;
    }

    public List<CloudMockService> explicitServices() {
        return explicitServices;
    }
}
