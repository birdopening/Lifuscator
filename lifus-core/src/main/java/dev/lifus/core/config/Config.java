package dev.lifus.core.config;

import java.nio.file.Path;

public record Config(Path input, Path output) {

    public static Config of(String input, String output) {
        return new Config(Path.of(input), Path.of(output));
    }
}
