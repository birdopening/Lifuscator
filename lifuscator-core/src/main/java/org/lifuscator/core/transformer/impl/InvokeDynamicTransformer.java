package org.lifuscator.core.transformer.impl;

import lombok.extern.slf4j.Slf4j;
import org.lifuscator.core.context.Context;
import org.lifuscator.core.transformer.Transformer;
import org.lifuscator.core.utils.AsmUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

//TODO: support Invokespecial + fields

@Slf4j(topic = "InvokeDynamic")
public class InvokeDynamicTransformer extends Transformer {

    public static final String BOOTSTRAP_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;I)Ljava/lang/invoke/CallSite;";
    public static final String REFERENCES_DESC = "[Ljava/lang/String;";

    @Override
    public void transform(Context context) {
        AtomicInteger count = new AtomicInteger(0);

        for (ClassNode clazz : context.getJar().classes().values()) {
            String bootstrapName = null;
            String referencesName = null;

            //TODO: maybe a reference record
            List<String> references = new ArrayList<>();

            for (MethodNode method : clazz.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode methodInsn && replaceable(methodInsn)) {
                        if (bootstrapName == null) {
                            bootstrapName = AsmUtils.findUnusedMethodName(clazz, "bootstrap", BOOTSTRAP_DESC);
                            referencesName = AsmUtils.findUnusedFieldName(clazz, "references", REFERENCES_DESC);
                        }

                        Handle bootstrap = new Handle(H_INVOKESTATIC, clazz.name, bootstrapName, BOOTSTRAP_DESC, false);
                        InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(refName(), indyDesc(methodInsn), bootstrap, references.size());
                        method.instructions.set(methodInsn, indy);
                        references.add(reference(methodInsn));

                        count.getAndIncrement();
                    }
                }
            }

            if (bootstrapName != null) {
                clazz.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, referencesName, REFERENCES_DESC, null, null));
                injectReferences(clazz, referencesName, references);
                injectBootstrapMethod(clazz, bootstrapName, referencesName);
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

    public String reference(MethodInsnNode methodInsn) {
        return methodInsn.owner + "%" + methodInsn.name + "%" + methodInsn.desc + "%" + methodInsn.getOpcode();
    }

    public String refName() {
        //could be anything
        return "ref" + random.nextInt();
    }

//    public static String[] references;
//
//    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType invokedType, int index) throws Throwable {
//        String[] reference = references[index].split("%", 4);
//        ClassLoader classLoader = lookup.lookupClass().getClassLoader();
//        Class<?> ownerClass = Class.forName(reference[0].replace("/", "."), false, classLoader);
//        String methodName = reference[1];
//        MethodType methodType = MethodType.fromMethodDescriptorString(reference[2], classLoader);
//        int opcode = Integer.parseInt(reference[3]);
//        MethodHandle handle = opcode == INVOKESTATIC ? lookup.findStatic(ownerClass, methodName, methodType) : lookup.findVirtual(ownerClass, methodName, methodType);
//        return new ConstantCallSite(handle.asType(invokedType));
//    }

    public void injectReferences(ClassNode clazz, String fieldName, List<String> references) {
        InsnList instructions = new InsnList();
        instructions.add(AsmUtils.numberInsn(references.size()));
        instructions.add(new TypeInsnNode(ANEWARRAY, "java/lang/String"));

        for (int i = 0; i < references.size(); i++) {
            instructions.add(new InsnNode(DUP));
            instructions.add(AsmUtils.numberInsn(i)); // index
            instructions.add(new LdcInsnNode(references.get(i)));
            instructions.add(new InsnNode(AASTORE));
        }

        instructions.add(new FieldInsnNode(PUTSTATIC, clazz.name, fieldName, REFERENCES_DESC));
        AsmUtils.clinit(clazz).instructions.insert(instructions);
    }

    public void injectBootstrapMethod(ClassNode clazz, String methodName, String referencesName) {
        MethodNode bootstrapMethod = new MethodNode(ACC_PUBLIC | ACC_STATIC, methodName, BOOTSTRAP_DESC, null, new String[]{"java/lang/Throwable"});
        bootstrapMethod.visitCode();
        bootstrapMethod.visitFieldInsn(GETSTATIC, clazz.name, referencesName, REFERENCES_DESC);
        bootstrapMethod.visitVarInsn(ILOAD, 3);
        bootstrapMethod.visitInsn(AALOAD);
        bootstrapMethod.visitLdcInsn("%");
        bootstrapMethod.visitInsn(ICONST_4);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;I)[Ljava/lang/String;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 4);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "lookupClass", "()Ljava/lang/Class;", false);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 5);
        bootstrapMethod.visitVarInsn(ALOAD, 4);
        bootstrapMethod.visitInsn(ICONST_0);
        bootstrapMethod.visitInsn(AALOAD);
        bootstrapMethod.visitLdcInsn("/");
        bootstrapMethod.visitLdcInsn(".");
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false);
        bootstrapMethod.visitInsn(ICONST_0);
        bootstrapMethod.visitVarInsn(ALOAD, 5);
        bootstrapMethod.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 4);
        bootstrapMethod.visitInsn(ICONST_1);
        bootstrapMethod.visitInsn(AALOAD);
        bootstrapMethod.visitVarInsn(ASTORE, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 4);
        bootstrapMethod.visitInsn(ICONST_2);
        bootstrapMethod.visitInsn(AALOAD);
        bootstrapMethod.visitVarInsn(ALOAD, 5);
        bootstrapMethod.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 8);
        bootstrapMethod.visitVarInsn(ALOAD, 4);
        bootstrapMethod.visitInsn(ICONST_3);
        bootstrapMethod.visitInsn(AALOAD);
        bootstrapMethod.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
        bootstrapMethod.visitVarInsn(ISTORE, 9);
        bootstrapMethod.visitVarInsn(ILOAD, 9);
        bootstrapMethod.visitIntInsn(SIPUSH, 184);
        Label label0 = new Label();
        bootstrapMethod.visitJumpInsn(IF_ICMPNE, label0);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 8);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        Label label1 = new Label();
        bootstrapMethod.visitJumpInsn(GOTO, label1);
        bootstrapMethod.visitLabel(label0);
        bootstrapMethod.visitFrame(F_FULL, 10, new Object[]{"java/lang/invoke/MethodHandles$Lookup", "java/lang/String", "java/lang/invoke/MethodType", INTEGER, "[Ljava/lang/String;", "java/lang/ClassLoader", "java/lang/Class", "java/lang/String", "java/lang/invoke/MethodType", INTEGER}, 0, new Object[]{});
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 8);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitLabel(label1);
        bootstrapMethod.visitFrame(F_SAME1, 0, null, 1, new Object[]{"java/lang/invoke/MethodHandle"});
        bootstrapMethod.visitVarInsn(ASTORE, 10);
        bootstrapMethod.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
        bootstrapMethod.visitInsn(DUP);
        bootstrapMethod.visitVarInsn(ALOAD, 10);
        bootstrapMethod.visitVarInsn(ALOAD, 2);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
        bootstrapMethod.visitInsn(ARETURN);
        bootstrapMethod.visitMaxs(4, 11);
        bootstrapMethod.visitEnd();
        clazz.methods.add(bootstrapMethod);
    }
}
