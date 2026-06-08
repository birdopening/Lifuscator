package org.lifuscator.core.analysis;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.Set;

public class ControlFlowAnalyzer {

    public Set<AbstractInsnNode> findLeaders(MethodNode method) {
        Set<AbstractInsnNode> leaders = new HashSet<>();

        AbstractInsnNode[] insns = method.instructions.toArray();
        if (insns.length == 0) {
            return leaders;
        }
        
        leaders.add(insns[0]);

        for (AbstractInsnNode insn : insns) {
            if (insn instanceof JumpInsnNode jump) {
                leaders.add(jump.label);
            } else if (insn instanceof TableSwitchInsnNode tableSwitch) {
                leaders.add(tableSwitch.dflt);
                leaders.addAll(tableSwitch.labels);
            } else if (insn instanceof LookupSwitchInsnNode lookupSwitch) {
                leaders.add(lookupSwitch.dflt);
                leaders.addAll(lookupSwitch.labels);
            }

            if (endsBlock(insn)) {
                AbstractInsnNode next = insn.getNext();
                if (next != null) {
                    leaders.add(next);
                }
            }
        }

        return leaders;
    }

    private boolean endsBlock(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();

        if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) {
            return true;
        }

        return (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW;
    }
}
