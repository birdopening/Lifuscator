package dev.lifus.core.transformer.impl;

import dev.lifus.core.context.Context;
import dev.lifus.core.transformer.Transformer;
import dev.lifus.core.utils.AsmUtils;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j(topic = "StringEncryptor")
public class StringEncryptorTransformer extends Transformer {

    public static final String XOR_DESC = "(Ljava/lang/String;I)Ljava/lang/String;";

    public static String xor(String s, int k) {
        char[] c = s.toCharArray();
        for (int i = 0; i < s.length(); i++) {
            c[i] = (char) (c[i] ^ k);
        }
        return new String(c);
    }

    @Override
    public String id() {
        return "string-encryptor";
    }

    @Override
    public String name() {
        return "String Encryptor";
    }

    @Override
    public String description() {
        return "Encrypts strings and decrypts them at runtime";
    }

    @Override
    public void transform(Context context) {

        AtomicInteger count = new AtomicInteger(0);


        List<ClassNode> classes = new ArrayList<>(context.getJar().classes().values());
        // 1 xor method
        ClassNode hostClass = pickHostClass(context, classes);
        String methodName = AsmUtils.findUnusedMethodName(hostClass, "xor", XOR_DESC);
        log.info("Host class for xor method: {}", hostClass.name);

        for (ClassNode clazz : classes) {
            for (MethodNode method : clazz.methods) {
                boolean encryptedAny = false;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof LdcInsnNode ldc) {
                        if (ldc.cst instanceof String string) {
                            int key = random.nextInt(255) + 1;
                            ldc.cst = xor(string, key);

                            InsnList decrypt = new InsnList();
                            decrypt.add(new LdcInsnNode(key));
                            decrypt.add(new MethodInsnNode(INVOKESTATIC, hostClass.name, methodName, XOR_DESC, false));
                            method.instructions.insert(ldc, decrypt);

                            encryptedAny = true;
                            count.getAndIncrement();
                        }
                    }
                }

                if (encryptedAny) {
                    method.maxStack += 1;
                }
            }
        }

        if (hostClass != null) {
            injectXorMethod(hostClass, methodName);
        }

        log.info("Encrypted {} strings", count.get());
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
