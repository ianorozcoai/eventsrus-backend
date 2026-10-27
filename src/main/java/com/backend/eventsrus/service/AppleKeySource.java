package com.backend.eventsrus.service;

import java.security.PublicKey;

/** Apple's public signing keys, looked up by the token header's key id. */
public interface AppleKeySource {

    /** @return the key, or null when Apple publishes no key with that id */
    PublicKey keyFor(String keyId);
}
