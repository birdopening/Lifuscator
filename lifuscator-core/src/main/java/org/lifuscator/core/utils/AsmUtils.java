package org.lifuscator.core.utils;

import lombok.experimental.UtilityClass;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

@UtilityClass
public class AsmUtils implements Opcodes {

    public static AbstractInsnNode numberInsn(int number) {
        if (number >= -1 && number <= 5) {
            return new InsnNode(number + 3);
        } else if (number >= Byte.MIN_VALUE && number <= Byte.MAX_VALUE) {
            return new IntInsnNode(BIPUSH, number);
        } else if (number >= Short.MIN_VALUE && number <= Short.MAX_VALUE) {
            return new IntInsnNode(SIPUSH, number);
        } else {
            return new LdcInsnNode(number);
        }
    }

    public Integer number(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();

        if (opcode >= ICONST_M1 && opcode <= ICONST_5) {
            return opcode - 3;
        }

        if (instruction instanceof IntInsnNode intInsn && (opcode == BIPUSH || opcode == SIPUSH)) {
            return intInsn.operand;
        }

        if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Integer integer) {
            return integer;
        }

        return null;
    }

    public String findUnusedMethodName(ClassNode clazz, String desiredName, String desc) {
        if (!methodExists(clazz, desiredName, desc)) {
            return desiredName;
        }

        String name;
        int i = 0;

        do {
            name = desiredName + i++;
        } while (methodExists(clazz, name, desc));

        return name;
    }

    public boolean methodExists(ClassNode clazz, String name, String desc) {
        for (MethodNode method : clazz.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    public String findUnusedFieldName(ClassNode clazz, String desiredName, String desc) {
        if (!fieldExists(clazz, desiredName, desc)) {
            return desiredName;
        }

        String name;
        int i = 0;

        do {
            name = desiredName + i++;
        } while (fieldExists(clazz, name, desc));

        return name;
    }

    public boolean fieldExists(ClassNode clazz, String name, String desc) {
        for (FieldNode field : clazz.fields) {
            if (field.name.equals(name) && field.desc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    public MethodNode clinit(ClassNode clazz) {
        for (MethodNode method : clazz.methods) {
            if (method.name.equals("<clinit>")) {
                return method;
            }
        }

        MethodNode clinit = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.instructions.add(new InsnNode(RETURN));
        clazz.methods.add(clinit);
        return clinit;
    }
}
