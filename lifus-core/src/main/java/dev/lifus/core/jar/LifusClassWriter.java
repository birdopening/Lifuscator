package dev.lifus.core.jar;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LifusClassWriter extends ClassWriter {

    private static final String OBJECT = "java/lang/Object";

    private final Map<String, ClassNode> classes;

    public LifusClassWriter(int flags, Map<String, ClassNode> classes) {
        super(flags);
        this.classes = classes;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        if (type1.equals(type2)) {
            return type1;
        }
        if (OBJECT.equals(type1) || OBJECT.equals(type2)) {
            return OBJECT;
        }

        Set<String> superTypes = new HashSet<>();
        String current = type1;
        while (current != null && !OBJECT.equals(current)) {
            superTypes.add(current);
            current = superName(current);
        }
        superTypes.add(OBJECT);

        current = type2;
        while (current != null) {
            if (superTypes.contains(current)) {
                return current;
            }
            current = superName(current);
        }

        return OBJECT;
    }

    private String superName(String type) {
        ClassNode node = classes.get(type);
        if (node != null) {
            return node.superName;
        }

        try {
            Class<?> clazz = Class.forName(type.replace('/', '.'), false, getClass().getClassLoader());
            if (clazz.isInterface()) {
                return OBJECT;
            }
            Class<?> superClass = clazz.getSuperclass();
            return superClass == null ? null : superClass.getName().replace('.', '/');
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }
}
