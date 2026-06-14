package dev.lifus.core.transformer;

import dev.lifus.core.context.Context;
import dev.lifus.core.registry.IFeature;
import dev.lifus.core.utils.AsmUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public abstract class Transformer implements Opcodes, IFeature {
    public final SecureRandom random = new SecureRandom();

    public abstract void transform(Context context);

    protected ClassNode pickHostClass(Context context, List<ClassNode> classes) {
        List<ClassNode> candidates = new ArrayList<>();
        for (ClassNode clazz : classes) {
            boolean isPublic = (clazz.access & ACC_PUBLIC) != 0;
            boolean isInterface = (clazz.access & ACC_INTERFACE) != 0;
            boolean toplevel = clazz.name.indexOf('$') < 0;
            if (isPublic && !isInterface && toplevel) {
                candidates.add(clazz);
            }
        }
        if (candidates.isEmpty()) {
            // practically never happens
            return injectHostClass(context);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    protected ClassNode injectHostClass(Context context) {
        ClassNode hostClass = new ClassNode();

        hostClass.visit(context.getJar().major(), ACC_PUBLIC | ACC_SUPER, AsmUtils.findUnusedClassName(context, "Host"), null, "java/lang/Object", null);

        MethodNode methodNode = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        methodNode.visitCode();
        methodNode.visitVarInsn(ALOAD, 0);
        methodNode.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodNode.visitInsn(RETURN);
        methodNode.visitMaxs(1, 1);
        methodNode.visitEnd();
        hostClass.methods.add(methodNode);

        hostClass.visitEnd();

        context.getJar().classes().put(hostClass.name, hostClass);
        return hostClass;
    }
}
