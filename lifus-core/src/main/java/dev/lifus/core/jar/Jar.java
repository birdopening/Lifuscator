package dev.lifus.core.jar;

import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public record Jar(Map<String, ClassNode> classes, Map<String, byte[]> resources, int major) {
}
