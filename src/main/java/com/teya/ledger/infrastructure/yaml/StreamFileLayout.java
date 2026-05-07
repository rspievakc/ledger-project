package com.teya.ledger.infrastructure.yaml;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Maps logical {@code streamId}s to YAML file paths under a root
 * directory. Validates ids to prevent path traversal.
 */
final class StreamFileLayout {

    private static final Pattern SAFE_STREAM_ID =
        Pattern.compile("[A-Za-z0-9._\\-]+");

    private final Path root;

    StreamFileLayout(Path root) {
        this.root = Objects.requireNonNull(root);
    }

    Path fileFor(String streamId) {
        if (!SAFE_STREAM_ID.matcher(streamId).matches()) {
            throw new IllegalArgumentException(
                "streamId must match " + SAFE_STREAM_ID.pattern() + ", got: " + streamId);
        }
        return root.resolve(streamId + ".yaml");
    }

    Path root() {
        return root;
    }
}
