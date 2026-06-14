package dev.lifus.core.context;

import lombok.Getter;
import lombok.Setter;
import dev.lifus.core.config.Config;
import dev.lifus.core.jar.Jar;
import dev.lifus.core.transformer.TransformerRegistry;

@Getter
public class Context {

    private final Config config;
    private final TransformerRegistry transformerRegistry = new TransformerRegistry();

    @Setter
    private Jar jar;

    public Context(Config config) {
        this.config = config;
    }
}
