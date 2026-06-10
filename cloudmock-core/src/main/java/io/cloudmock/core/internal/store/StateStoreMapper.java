package io.cloudmock.core.internal.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

/**
 * Builds the {@link ObjectMapper} shared by the persistent {@link io.cloudmock.core.spi.StateStore}
 * backends.
 *
 * <p>Default typing is activated so each value records its concrete Java type and is read back as
 * that type after a restart — not as a generic map. This is what gives type fidelity to both
 * {@link JsonFileStateStore} and {@link AppendLogStateStore}. It is safe here because a store only
 * ever reads back its own locally-written file, never untrusted input. Stored value types must be on
 * the classpath when the store is reloaded.
 */
final class StateStoreMapper {

    private StateStoreMapper() {}

    static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }
}
