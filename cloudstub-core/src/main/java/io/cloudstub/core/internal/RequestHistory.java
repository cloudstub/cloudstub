package io.cloudstub.core.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.cloudstub.core.restapi.RequestRecord;
import java.util.List;

/**
 * Read/clear view over the WireMock request journal, translated into CloudStub's {@link
 * RequestRecord} domain model.
 *
 * <p>Encapsulates the {@code cloudstub:<serviceId>:<operation>} stub-naming convention used to
 * correlate a served request back to the module that handled it, keeping that knowledge out of the
 * engine entry point.
 */
public final class RequestHistory {

    private static final String STUB_NAME_PREFIX = "cloudstub:";

    private final WireMockServer server;

    public RequestHistory(WireMockServer server) {
        this.server = server;
    }

    /** All served requests, newest first. */
    public List<RequestRecord> all() {
        return server.getAllServeEvents().stream().map(RequestHistory::toRecord).toList();
    }

    /** Served requests handled by {@code serviceId}, newest first; unmatched requests excluded. */
    public List<RequestRecord> forService(String serviceId) {
        return server.getAllServeEvents().stream()
                .map(RequestHistory::toRecord)
                .filter(record -> serviceId.equals(record.serviceId()))
                .toList();
    }

    /** Discards all recorded requests. */
    public void clear() {
        server.resetRequests();
    }

    private static RequestRecord toRecord(ServeEvent event) {
        LoggedRequest req = event.getRequest();
        String serviceId = null;
        String operation = null;
        if (event.getWasMatched() && event.getStubMapping() != null) {
            String name = event.getStubMapping().getName();
            if (name != null && name.startsWith(STUB_NAME_PREFIX)) {
                String[] parts = name.split(":", 3);
                if (parts.length == 3) {
                    serviceId = parts[1];
                    operation = parts[2];
                }
            }
        }
        int statusCode = event.getResponse() != null ? event.getResponse().getStatus() : -1;
        return new RequestRecord(
                req.getLoggedDate().toInstant().toString(),
                req.getMethod().value(),
                req.getUrl(),
                serviceId,
                operation,
                statusCode,
                event.getWasMatched());
    }
}
