package org.lifuscator.core.context;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;
import org.lifuscator.core.jar.Jar;
import org.lifuscator.core.jar.JarLoader;
import org.lifuscator.core.transformer.Transformer;
import org.lifuscator.core.transformer.impl.SourceFileRemoverTransformer;
import org.lifuscator.core.transformer.impl.StringEncryptorTransformer;
import org.lifuscator.core.utils.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Getter
@Slf4j(topic = "Context")
public class Context {

    private final Path input;
    private final Path output;

    private Jar jar;

    private final List<Transformer> transformers = new ArrayList<>();

    public Context(String input, String output) {
        this.input = Path.of(input);
        this.output = Path.of(output);

        transformers.add(new SourceFileRemoverTransformer());
        transformers.add(new StringEncryptorTransformer());
    }

    public void run() {
        this.jar = JarLoader.load(this.input);
        if (this.jar == null) {
            log.error("Failed to load jar");
            return;
        }

        for (Transformer transformer : this.transformers) {
            log.info("Running transformer {}", transformer.getClass().getSimpleName());
            transformer.transform(this);
        }

        if (!JarLoader.export(this.jar, this.output)) {
            log.error("Failed to export jar");
            return;
        }

        log.info("Successful!");

        try {
            String oldSize = FileUtils.byteCountToDisplaySize(PathUtils.sizeOf(this.input));
            String newSize = FileUtils.byteCountToDisplaySize(PathUtils.sizeOf(this.output));
            log.info("File size changed from {} to {}", oldSize, newSize);
        } catch (IOException e) {
            log.error("Failed to get jar size", e);
        }
    }
}
