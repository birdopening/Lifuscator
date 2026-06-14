package org.lifuscator.core.context;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;
import org.lifuscator.core.config.Config;
import org.lifuscator.core.jar.Jar;
import org.lifuscator.core.jar.JarLoader;
import org.lifuscator.core.transformer.Transformer;
import org.lifuscator.core.transformer.impl.IntegerEncryptorTransformer;
import org.lifuscator.core.transformer.impl.InvokeDynamicTransformer;
import org.lifuscator.core.transformer.impl.SourceFileRemoverTransformer;
import org.lifuscator.core.transformer.impl.StringEncryptorTransformer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
@Slf4j(topic = "Context")
public class Context {

    private final Config config;
    private final List<Transformer> transformers = new ArrayList<>();
    private Jar jar;

    public Context(Config config) {
        this.config = config;

        transformers.add(new SourceFileRemoverTransformer());
        transformers.add(new StringEncryptorTransformer());
        transformers.add(new IntegerEncryptorTransformer());
        transformers.add(new InvokeDynamicTransformer());
    }

    public void run() {
        this.jar = JarLoader.load(this.config.input());
        if (this.jar == null) {
            log.error("Failed to load jar");
            return;
        }

        for (Transformer transformer : this.transformers) {
            log.info("Running transformer {}", transformer.getClass().getSimpleName());
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
