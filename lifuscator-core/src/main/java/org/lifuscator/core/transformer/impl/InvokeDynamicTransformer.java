package org.lifuscator.core.transformer.impl;

import lombok.extern.slf4j.Slf4j;
import org.lifuscator.core.context.Context;
import org.lifuscator.core.transformer.Transformer;
import org.lifuscator.core.utils.AsmUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import java.util.concurrent.atomic.AtomicInteger;

//TODO: support Invokespecial + fields

@Slf4j(topic = "InvokeDynamic")
public class InvokeDynamicTransformer extends Transformer {

    public static final String BOOTSTRAP_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;";

    @Override
    public void transform(Context context) {
        AtomicInteger count = new AtomicInteger(0);

        for (ClassNode clazz : context.getJar().classes().values()) {
            String bootstrapName = null;

            for (MethodNode method : clazz.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode methodInsn && replaceable(methodInsn)) {
                        if (bootstrapName == null) {
                            bootstrapName = AsmUtils.findUnusedMethodName(clazz, "bootstrap", BOOTSTRAP_DESC);
                        }

                        Handle bootstrap = new Handle(H_INVOKESTATIC, clazz.name, bootstrapName, BOOTSTRAP_DESC, false);
                        InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(methodInsn.name, indyDesc(methodInsn), bootstrap, methodInsn.owner, methodInsn.desc, methodInsn.getOpcode());
                        method.instructions.set(methodInsn, indy);

                        count.getAndIncrement();
                    }
                }
            }

            if (bootstrapName != null) {
                injectBootstrapMethod(clazz, bootstrapName);
            }
        }

        log.info("Replaced {} method calls", count.get());
    }

    public boolean replaceable(MethodInsnNode methodInsn) {
        int opcode = methodInsn.getOpcode();
        return opcode == INVOKESTATIC || opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE;
    }

    public String indyDesc(MethodInsnNode methodInsn) {
        if (methodInsn.getOpcode() == INVOKESTATIC) {
            return methodInsn.desc;
        }
        return methodInsn.desc.replace("(", "(Ljava/lang/Object;");
    }

//    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType invokedType, String owner, String desc, int opcode) throws Throwable {
//        ClassLoader classLoader = lookup.lookupClass().getClassLoader();
//        Class<?> ownerClass = Class.forName(owner.replace("/", "."), false, classLoader);
//        MethodType methodType = MethodType.fromMethodDescriptorString(desc, classLoader);
//        MethodHandle handle = opcode == INVOKESTATIC ? lookup.findStatic(ownerClass, name, methodType) : lookup.findVirtual(ownerClass, name, methodType);
//        return new ConstantCallSite(handle.asType(invokedType));
//    }

    public void injectBootstrapMethod(ClassNode clazz, String methodName) {
        MethodNode bootstrapMethod = new MethodNode(ACC_PUBLIC | ACC_STATIC, methodName, BOOTSTRAP_DESC, null, new String[]{"java/lang/Throwable"});
        bootstrapMethod.visitCode();
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "lookupClass", "()Ljava/lang/Class;", false);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 3);
        bootstrapMethod.visitLdcInsn("/");
        bootstrapMethod.visitLdcInsn(".");
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false);
        bootstrapMethod.visitInsn(ICONST_0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 4);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 8);
        bootstrapMethod.visitVarInsn(ILOAD, 5);
        bootstrapMethod.visitIntInsn(SIPUSH, 184);
        Label label0 = new Label();
        bootstrapMethod.visitJumpInsn(IF_ICMPNE, label0);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 1);
        bootstrapMethod.visitVarInsn(ALOAD, 8);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        Label label1 = new Label();
        bootstrapMethod.visitJumpInsn(GOTO, label1);
        bootstrapMethod.visitLabel(label0);
        bootstrapMethod.visitFrame(F_APPEND, 3, new Object[]{"java/lang/ClassLoader", "java/lang/Class", "java/lang/invoke/MethodType"}, 0, null);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 1);
        bootstrapMethod.visitVarInsn(ALOAD, 8);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitLabel(label1);
        bootstrapMethod.visitFrame(F_SAME1, 0, null, 1, new Object[]{"java/lang/invoke/MethodHandle"});
        bootstrapMethod.visitVarInsn(ASTORE, 9);
        bootstrapMethod.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
        bootstrapMethod.visitInsn(DUP);
        bootstrapMethod.visitVarInsn(ALOAD, 9);
        bootstrapMethod.visitVarInsn(ALOAD, 2);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
        bootstrapMethod.visitInsn(ARETURN);
        bootstrapMethod.visitMaxs(4, 10);
        bootstrapMethod.visitEnd();
        clazz.methods.add(bootstrapMethod);
    }
}
