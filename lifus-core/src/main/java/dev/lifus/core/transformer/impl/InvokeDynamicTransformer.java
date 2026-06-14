package dev.lifus.core.transformer.impl;

import lombok.extern.slf4j.Slf4j;
import dev.lifus.core.context.Context;
import dev.lifus.core.jar.Jar;
import dev.lifus.core.transformer.Transformer;
import dev.lifus.core.utils.AsmUtils;
import dev.lifus.core.utils.NameUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

//TODO: support Invokespecial

@Slf4j(topic = "InvokeDynamic")
public class InvokeDynamicTransformer extends Transformer {

    public static final String BOOTSTRAP_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;I)Ljava/lang/invoke/CallSite;";
    public static final String REFERENCES_DESC = "[Ljava/lang/String;";

    @Override
    public String id() {
        return "invoke-dynamic";
    }

    @Override
    public String name() {
        return "InvokeDynamic";
    }

    @Override
    public String description() {
        return "Hides method/field references behind invokedynamic call sites";
    }

    @Override
    public void transform(Context context) {
        AtomicInteger methodCount = new AtomicInteger(0);
        AtomicInteger fieldCount = new AtomicInteger(0);

        for (ClassNode clazz : context.getJar().classes().values()) {
            if ((clazz.access & ACC_INTERFACE) != 0) {
                continue;
            }

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
                        InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(NameUtils.weirdName(), indyDesc(methodInsn), bootstrap, references.size());
                        method.instructions.set(methodInsn, indy);
                        references.add(reference(methodInsn));

                        methodCount.getAndIncrement();
                    } else if (instruction instanceof FieldInsnNode fieldInsn && replaceable(context.getJar(), fieldInsn)) {
                        if (bootstrapName == null) {
                            bootstrapName = AsmUtils.findUnusedMethodName(clazz, "bootstrap", BOOTSTRAP_DESC);
                            referencesName = AsmUtils.findUnusedFieldName(clazz, "references", REFERENCES_DESC);
                        }

                        Handle bootstrap = new Handle(H_INVOKESTATIC, clazz.name, bootstrapName, BOOTSTRAP_DESC, false);
                        InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(NameUtils.weirdName(), indyDesc(fieldInsn), bootstrap, references.size());
                        method.instructions.set(fieldInsn, indy);
                        references.add(reference(fieldInsn));

                        fieldCount.getAndIncrement();
                    }
                }
            }

            if (bootstrapName != null) {
                clazz.fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, referencesName, REFERENCES_DESC, null, null));
                injectReferences(clazz, referencesName, references);
                injectBootstrapMethod(clazz, bootstrapName, referencesName);
            }
        }

        log.info("Replaced {} method calls and {} field references", methodCount.get(), fieldCount.get());
    }

    public boolean replaceable(MethodInsnNode methodInsn) {
        int opcode = methodInsn.getOpcode();
        return opcode == INVOKESTATIC || opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE;
    }

    public boolean replaceable(Jar jar, FieldInsnNode fieldInsn) {
        FieldNode field = findField(jar, fieldInsn);
        if (field == null) {
            return false;
        }

        int opcode = fieldInsn.getOpcode();
        if (opcode == GETSTATIC || opcode == GETFIELD) {
            return true;
        }

        // exclude final
        if (opcode == PUTSTATIC || opcode == PUTFIELD) {
            return (field.access & ACC_FINAL) == 0;
        }

        return false;
    }

    public FieldNode findField(Jar jar, FieldInsnNode fieldInsn) {
        Set<String> visited = new HashSet<>();
        List<String> owners = new ArrayList<>();

        owners.add(fieldInsn.owner); // start

        for (int i = 0; i < owners.size(); i++) {
            String ownerName = owners.get(i);
            if (ownerName == null || ownerName.equals("java/lang/Object") || !visited.add(ownerName)) {
                continue;
            }

            ClassNode owner = jar.classes().get(ownerName);
            if (owner == null) {
                continue;
            }

            for (FieldNode field : owner.fields) {
                if (field.name.equals(fieldInsn.name) && field.desc.equals(fieldInsn.desc)) {
                    return field; // found
                }
            }

            owners.addAll(owner.interfaces);
            owners.add(owner.superName);
        }

        return null;
    }

    public String indyDesc(MethodInsnNode methodInsn) {
        if (methodInsn.getOpcode() == INVOKESTATIC) {
            return methodInsn.desc;
        }
        return methodInsn.desc.replace("(", "(Ljava/lang/Object;");
    }

    public String indyDesc(FieldInsnNode fieldInsn) {
        return switch (fieldInsn.getOpcode()) {
            case GETSTATIC -> "()" + fieldInsn.desc;
            case PUTSTATIC -> "(" + fieldInsn.desc + ")V";
            case GETFIELD -> "(Ljava/lang/Object;)" + fieldInsn.desc;
            case PUTFIELD -> "(Ljava/lang/Object;" + fieldInsn.desc + ")V";
            default -> throw new IllegalArgumentException("Unsupported field opcode: " + fieldInsn.getOpcode());
        };
    }

    public String reference(MethodInsnNode methodInsn) {
        return methodInsn.owner + "%" + methodInsn.name + "%" + methodInsn.desc + "%" + methodInsn.getOpcode();
    }

    public String reference(FieldInsnNode fieldInsn) {
        return fieldInsn.owner + "%" + fieldInsn.name + "%" + fieldInsn.desc + "%" + fieldInsn.getOpcode();
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
//        String memberName = reference[1];
//        String descriptor = reference[2];
//        int opcode = Integer.parseInt(reference[3]);
//        MethodHandle handle;
//
//        if (opcode == INVOKESTATIC || opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE) {
//            MethodType methodType = MethodType.fromMethodDescriptorString(descriptor, classLoader);
//            handle = opcode == INVOKESTATIC ? lookup.findStatic(ownerClass, memberName, methodType) : lookup.findVirtual(ownerClass, memberName, methodType);
//        } else {
//            Class<?> fieldType = MethodType.fromMethodDescriptorString("()" + descriptor, classLoader).returnType();
//            handle = switch (opcode) {
//                case GETSTATIC -> lookup.findStaticGetter(ownerClass, memberName, fieldType);
//                case PUTSTATIC -> lookup.findStaticSetter(ownerClass, memberName, fieldType);
//                case GETFIELD -> lookup.findGetter(ownerClass, memberName, fieldType);
//                case PUTFIELD -> lookup.findSetter(ownerClass, memberName, fieldType);
//                default -> throw new IllegalArgumentException("Unsupported reference opcode: " + opcode);
//            };
//        }
//
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
        bootstrapMethod.visitVarInsn(ASTORE, 8);
        bootstrapMethod.visitVarInsn(ALOAD, 4);
        bootstrapMethod.visitInsn(ICONST_3);
        bootstrapMethod.visitInsn(AALOAD);
        bootstrapMethod.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
        bootstrapMethod.visitVarInsn(ISTORE, 9);
        bootstrapMethod.visitVarInsn(ILOAD, 9);
        bootstrapMethod.visitIntInsn(SIPUSH, 184);
        Label label0 = new Label();
        bootstrapMethod.visitJumpInsn(IF_ICMPEQ, label0);
        bootstrapMethod.visitVarInsn(ILOAD, 9);
        bootstrapMethod.visitIntInsn(SIPUSH, 182);
        bootstrapMethod.visitJumpInsn(IF_ICMPEQ, label0);
        bootstrapMethod.visitVarInsn(ILOAD, 9);
        bootstrapMethod.visitIntInsn(SIPUSH, 185);
        Label label1 = new Label();
        bootstrapMethod.visitJumpInsn(IF_ICMPNE, label1);
        bootstrapMethod.visitLabel(label0);
        bootstrapMethod.visitFrame(F_FULL, 10, new Object[]{"java/lang/invoke/MethodHandles$Lookup", "java/lang/String", "java/lang/invoke/MethodType", INTEGER, "[Ljava/lang/String;", "java/lang/ClassLoader", "java/lang/Class", "java/lang/String", "java/lang/String", INTEGER}, 0, new Object[]{});
        bootstrapMethod.visitVarInsn(ALOAD, 8);
        bootstrapMethod.visitVarInsn(ALOAD, 5);
        bootstrapMethod.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 11);
        bootstrapMethod.visitVarInsn(ILOAD, 9);
        bootstrapMethod.visitIntInsn(SIPUSH, 184);
        Label label2 = new Label();
        bootstrapMethod.visitJumpInsn(IF_ICMPNE, label2);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 11);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        Label label3 = new Label();
        bootstrapMethod.visitJumpInsn(GOTO, label3);
        bootstrapMethod.visitLabel(label2);
        bootstrapMethod.visitFrame(F_APPEND, 2, new Object[]{TOP, "java/lang/invoke/MethodType"}, 0, null);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 11);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitLabel(label3);
        bootstrapMethod.visitFrame(F_SAME1, 0, null, 1, new Object[]{"java/lang/invoke/MethodHandle"});
        bootstrapMethod.visitVarInsn(ASTORE, 10);
        Label label4 = new Label();
        bootstrapMethod.visitJumpInsn(GOTO, label4);
        bootstrapMethod.visitLabel(label1);
        bootstrapMethod.visitFrame(F_CHOP, 2, null, 0, null);
        bootstrapMethod.visitVarInsn(ALOAD, 8);
        bootstrapMethod.visitInvokeDynamicInsn("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", new Handle(H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), "()\u0001");
        bootstrapMethod.visitVarInsn(ALOAD, 5);
        bootstrapMethod.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "returnType", "()Ljava/lang/Class;", false);
        bootstrapMethod.visitVarInsn(ASTORE, 11);
        bootstrapMethod.visitVarInsn(ILOAD, 9);
        Label label5 = new Label();
        Label label6 = new Label();
        Label label7 = new Label();
        Label label8 = new Label();
        Label label9 = new Label();
        bootstrapMethod.visitTableSwitchInsn(178, 181, label9, label5, label6, label7, label8);
        bootstrapMethod.visitLabel(label5);
        bootstrapMethod.visitFrame(F_APPEND, 2, new Object[]{TOP, "java/lang/Class"}, 0, null);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 11);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStaticGetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        Label label10 = new Label();
        bootstrapMethod.visitJumpInsn(GOTO, label10);
        bootstrapMethod.visitLabel(label6);
        bootstrapMethod.visitFrame(F_SAME, 0, null, 0, null);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 11);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStaticSetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitJumpInsn(GOTO, label10);
        bootstrapMethod.visitLabel(label7);
        bootstrapMethod.visitFrame(F_SAME, 0, null, 0, null);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 11);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findGetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitJumpInsn(GOTO, label10);
        bootstrapMethod.visitLabel(label8);
        bootstrapMethod.visitFrame(F_SAME, 0, null, 0, null);
        bootstrapMethod.visitVarInsn(ALOAD, 0);
        bootstrapMethod.visitVarInsn(ALOAD, 6);
        bootstrapMethod.visitVarInsn(ALOAD, 7);
        bootstrapMethod.visitVarInsn(ALOAD, 11);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findSetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitJumpInsn(GOTO, label10);
        bootstrapMethod.visitLabel(label9);
        bootstrapMethod.visitFrame(F_SAME, 0, null, 0, null);
        bootstrapMethod.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        bootstrapMethod.visitInsn(DUP);
        bootstrapMethod.visitVarInsn(ILOAD, 9);
        bootstrapMethod.visitInvokeDynamicInsn("makeConcatWithConstants", "(I)Ljava/lang/String;", new Handle(H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), "Unsupported reference opcode: \u0001");
        bootstrapMethod.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false);
        bootstrapMethod.visitInsn(ATHROW);
        bootstrapMethod.visitLabel(label10);
        bootstrapMethod.visitFrame(F_SAME1, 0, null, 1, new Object[]{"java/lang/invoke/MethodHandle"});
        bootstrapMethod.visitVarInsn(ASTORE, 10);
        bootstrapMethod.visitLabel(label4);
        bootstrapMethod.visitFrame(F_FULL, 11, new Object[]{"java/lang/invoke/MethodHandles$Lookup", "java/lang/String", "java/lang/invoke/MethodType", INTEGER, "[Ljava/lang/String;", "java/lang/ClassLoader", "java/lang/Class", "java/lang/String", "java/lang/String", INTEGER, "java/lang/invoke/MethodHandle"}, 0, new Object[]{});
        bootstrapMethod.visitTypeInsn(NEW, "java/lang/invoke/ConstantCallSite");
        bootstrapMethod.visitInsn(DUP);
        bootstrapMethod.visitVarInsn(ALOAD, 10);
        bootstrapMethod.visitVarInsn(ALOAD, 2);
        bootstrapMethod.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        bootstrapMethod.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
        bootstrapMethod.visitInsn(ARETURN);
        bootstrapMethod.visitMaxs(4, 12);
        bootstrapMethod.visitEnd();
        clazz.methods.add(bootstrapMethod);
    }
}
