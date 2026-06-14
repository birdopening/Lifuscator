package org.lifuscator.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;
import org.lifuscator.core.context.Context;
import org.lifuscator.core.jar.Jar;
import org.lifuscator.core.jar.JarLoader;
import org.lifuscator.core.transformer.Transformer;

import java.io.IOException;

@Slf4j(topic = "Lifuscator")
public class Lifuscator {

    public boolean run(Context context) {
        if (!load(context)) {
            return false;
        }
        transform(context);
        if (!export(context)) {
            return false;
        }
        report(context);
        return true;
    }

    private boolean load(Context context) {
        context.setJar(JarLoader.load(context.getConfig().input()));
        if (context.getJar() == null) {
            log.error("Failed to load jar");
            return false;
        }
        return true;
    }

    private void transform(Context context) {
        for (Transformer transformer : context.getTransformerRegistry().getEntries().values()) {
            log.info("Running transformer {}", transformer.id());
            transformer.transform(context);
        }
    }

    private boolean export(Context context) {
        Jar jar = context.getJar();
        if (!JarLoader.export(jar, context.getConfig().output())) {
            log.error("Failed to export jar");
            return false;
        }
        log.info("Successful!");
        return true;
    }

    private void report(Context context) {
        try {
            String oldSize = FileUtils.byteCountToDisplaySize(PathUtils.sizeOf(context.getConfig().input()));
            String newSize = FileUtils.byteCountToDisplaySize(PathUtils.sizeOf(context.getConfig().output()));
            log.info("File size changed from {} to {}", oldSize, newSize);
        } catch (IOException e) {
            log.error("Failed to get jar size", e);
        }
    }
}
