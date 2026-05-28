package org.lifuscator.utils;

import lombok.experimental.UtilityClass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

@UtilityClass
public class AsmUtils {

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
}
