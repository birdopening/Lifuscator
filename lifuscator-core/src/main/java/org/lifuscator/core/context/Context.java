package org.lifuscator.core.context;

import lombok.Getter;
import lombok.Setter;
import org.lifuscator.core.config.Config;
import org.lifuscator.core.jar.Jar;
import org.lifuscator.core.transformer.TransformerRegistry;

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
