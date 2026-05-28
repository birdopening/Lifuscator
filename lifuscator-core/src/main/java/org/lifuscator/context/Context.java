package org.lifuscator.context;

import lombok.Getter;
import org.lifuscator.utils.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Getter
public class Context {

    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Map<String, byte[]> resources = new HashMap<>();

    public void run(String input, String output) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(new File(input).toPath()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.getName().endsWith(".class")) {
                    ClassReader reader = new ClassReader(zis);
                    ClassNode classNode = new ClassNode();
                    reader.accept(classNode, 0);
                    classes.put(classNode.name, classNode);
                } else {
                    resources.put(zipEntry.getName(), IOUtils.readAll(zis));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
