package org.lifuscator.core.context;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;
import org.lifuscator.core.config.Config;
import org.lifuscator.core.jar.Jar;
import org.lifuscator.core.jar.JarLoader;
import org.lifuscator.core.transformer.Transformer;
import org.lifuscator.core.transformer.TransformerRegistry;

import java.io.IOException;

@Getter
@Slf4j(topic = "Context")
public class Context {

    private final Config config;
    private final TransformerRegistry transformerRegistry = new TransformerRegistry();
    private Jar jar;

    public Context(Config config) {
        this.config = config;
    }

    public void run() {
        this.jar = JarLoader.load(this.config.input());
        if (this.jar == null) {
            log.error("Failed to load jar");
            return;
        }

        for (Transformer transformer : this.transformerRegistry.getEntries().values()) {
            log.info("Running transformer {}", transformer.id());
            transformer.transform(this);
        }

        if (!JarLoader.export(this.jar, this.config.output())) {
            log.error("Failed to export jar");
            return;
        }

        log.info("Successful!");

        try {
            String oldSize = FileUtils.byteCountToDisplaySize(PathUtils.sizeOf(this.config.input()));
            String newSize = FileUtils.byteCountToDisplaySize(PathUtils.sizeOf(this.config.output()));
            log.info("File size changed from {} to {}", oldSize, newSize);
        } catch (IOException e) {
            log.error("Failed to get jar size", e);
        }
    }
}
