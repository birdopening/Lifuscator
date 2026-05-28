package org.lifuscator.transformer.impl;

import org.lifuscator.context.Context;
import org.lifuscator.transformer.Transformer;
import org.lifuscator.utils.AsmUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

public class StringEncryptorTransformer extends Transformer {

    public static final String XOR_DESC = "(Ljava/lang/String;I)Ljava/lang/String;";

    @Override
    public void transform(Context context) {
        for (ClassNode clazz : context.getClasses().values()) {
            String methodName = null;

            for (MethodNode method : clazz.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof LdcInsnNode ldc) {
                        if (ldc.cst instanceof String string) {
                            if (methodName == null) {
                                methodName = AsmUtils.findUnusedMethodName(clazz, "xor", XOR_DESC);
                            }

                            int key = random.nextInt(255) + 1;
                            ldc.cst = xor(string, key);

                            InsnList decrypt = new InsnList();
                            decrypt.add(new LdcInsnNode(key));
                            decrypt.add(new MethodInsnNode(INVOKESTATIC, clazz.name, methodName, XOR_DESC, false));
                            method.instructions.insert(ldc, decrypt);
                        }
                    }
                }
            }

            if (methodName != null) {
                injectXorMethod(clazz, methodName);
            }
        }
    }

    public static String xor(String s, int k) {
        char[] c = s.toCharArray();
        for (int i = 0; i < s.length(); i++) {
            c[i] = (char) (c[i] ^ k);
        }
        return new String(c);
    }

    public void injectXorMethod(ClassNode clazz, String methodName) {
        MethodNode xorMethod = new MethodNode(ACC_PUBLIC | ACC_STATIC, methodName, XOR_DESC, null, null);
        xorMethod.visitCode();
        xorMethod.visitVarInsn(ALOAD, 0);
        xorMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
        xorMethod.visitVarInsn(ASTORE, 2);
        xorMethod.visitInsn(ICONST_0);
        xorMethod.visitVarInsn(ISTORE, 3);
        Label label0 = new Label();
        xorMethod.visitLabel(label0);
        xorMethod.visitFrame(F_APPEND, 2, new Object[]{"[C", INTEGER}, 0, null);
        xorMethod.visitVarInsn(ILOAD, 3);
        xorMethod.visitVarInsn(ALOAD, 0);
        xorMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        Label label1 = new Label();
        xorMethod.visitJumpInsn(IF_ICMPGE, label1);
        xorMethod.visitVarInsn(ALOAD, 2);
        xorMethod.visitVarInsn(ILOAD, 3);
        xorMethod.visitVarInsn(ALOAD, 0);
        xorMethod.visitVarInsn(ILOAD, 3);
        xorMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
        xorMethod.visitVarInsn(ILOAD, 1);
        xorMethod.visitInsn(IXOR);
        xorMethod.visitInsn(I2C);
        xorMethod.visitInsn(CASTORE);
        xorMethod.visitIincInsn(3, 1);
        xorMethod.visitJumpInsn(GOTO, label0);
        xorMethod.visitLabel(label1);
        xorMethod.visitFrame(F_CHOP, 1, null, 0, null);
        xorMethod.visitTypeInsn(NEW, "java/lang/String");
        xorMethod.visitInsn(DUP);
        xorMethod.visitVarInsn(ALOAD, 2);
        xorMethod.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V", false);
        xorMethod.visitInsn(ARETURN);
        xorMethod.visitMaxs(4, 4);
        xorMethod.visitEnd();
        clazz.methods.add(xorMethod);
    }
}
