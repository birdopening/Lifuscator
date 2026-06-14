package org.lifuscator.core.jar;

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

@Slf4j(topic = "JarLoader")
public final class JarLoader {

    public static Jar load(Path input) {
        Map<String, ClassNode> classes = new HashMap<>();
        Map<String, byte[]> resources = new HashMap<>();

        try (JarFile jarFile = new JarFile(input.toFile())) {
            Enumeration<? extends JarEntry> entries = jarFile.entries();
            JarEntry entry;

            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    InputStream stream = jarFile.getInputStream(entry);
                    byte[] entryBytes = stream.readAllBytes();

                    if (hasClassBytes(entryBytes) && entry.getName().endsWith(".class")) {
                        ClassReader classReader = new ClassReader(entryBytes);
                        ClassNode classNode = new ClassNode();

                        classReader.accept(classNode, 0);
                        classes.put(classNode.name, classNode);
                    } else {
                        resources.put(entry.getName(), entryBytes);
                    }
                }
            }

            log.info("Loaded {} classes and {} resources", classes.size(), resources.size());

            int major = classes.values().stream().mapToInt(c -> c.version).max().getAsInt();

            return new Jar(classes, resources, major);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    public static boolean export(Jar jar, Path output) {
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(output))) {

            for (Map.Entry<String, byte[]> resource : jar.resources().entrySet()) {
                jos.putNextEntry(new JarEntry(resource.getKey()));
                jos.write(resource.getValue());
            }

            for (ClassNode clazz : jar.classes().values()) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                clazz.accept(writer);

                byte[] bytes = writer.toByteArray();
                jos.putNextEntry(new JarEntry(clazz.name + ".class"));
                jos.write(bytes);
            }
            return true;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return false;
    }

    private static boolean hasClassBytes(byte[] classBytes) {
        return classBytes != null && classBytes.length > 4
                && classBytes[0] == (byte) 0xCA
                && classBytes[1] == (byte) 0xFE
                && classBytes[2] == (byte) 0xBA
                && classBytes[3] == (byte) 0xBE;
    }

}
