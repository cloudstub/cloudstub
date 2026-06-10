package io.cloudmock.core.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the active fault, if any, for each service. The fault is applied as a decoration over the
 * matched stub's real response by {@link CloudMockResponseTransformer}; this class only tracks which
 * fault is active, so injecting or clearing a fault never touches the underlying stubs.
 *
 * <p>Clearing a single service affects only that service; other services and the stubs themselves
 * are untouched.
 */
public class FaultEngine {

    enum Type { THROTTLE, TIMEOUT, BROWNOUT }

    /** An active fault; {@code rate} is meaningful only for {@link Type#BROWNOUT}. */
    record Fault(Type type, double rate) {}

    private final Map<String, Fault> byService = new ConcurrentHashMap<>();

    public void injectThrottle(String serviceId) {
        byService.put(serviceId, new Fault(Type.THROTTLE, 0.0));
    }

    public void injectTimeout(String serviceId) {
        byService.put(serviceId, new Fault(Type.TIMEOUT, 0.0));
    }

    public void injectBrownout(String serviceId, double rate) {
        if (rate <= 0.0) {
            return;   // a zero-rate brownout never faults, so there is nothing to record
        }
        byService.put(serviceId, new Fault(Type.BROWNOUT, rate));
    }

    public void clearFaults(String serviceId) {
        byService.remove(serviceId);
    }

    public void clearAllFaults() {
        byService.clear();
    }

    /** The active fault for {@code serviceId}, or {@code null} if none is set. */
    Fault faultFor(String serviceId) {
        return byService.get(serviceId);
    }
}
