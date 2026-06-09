package org.lifuscator.core.analysis;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

public class ControlFlowAnalyzer {
    
    public List<BasicBlock> analyze(MethodNode method) {
        List<BasicBlock> blocks = buildBlocks(method);
        connectBlocks(blocks);
        return blocks;
    }

    public void connectBlocks(List<BasicBlock> blocks) {
        Map<AbstractInsnNode, BasicBlock> byLeader = new HashMap<>();
        for (BasicBlock block : blocks) {
            byLeader.put(block.first(), block);
        }

        for (int i = 0; i < blocks.size(); i++) {
            BasicBlock block = blocks.get(i);
            AbstractInsnNode last = block.last();
            int opcode = last.getOpcode();

            // returns and throw go nowhere
            if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
                continue;
            }

            switch (last) {
                case JumpInsnNode jump -> {
                    link(block, byLeader.get(jump.label));

                    // conditional
                    if (opcode != Opcodes.GOTO && opcode != Opcodes.JSR) {
                        link(block, fallThrough(blocks, i));
                    }
                }
                case TableSwitchInsnNode tableSwitch -> {
                    // default + every case label
                    link(block, byLeader.get(tableSwitch.dflt));
                    tableSwitch.labels.forEach(label -> link(block, byLeader.get(label)));
                }
                case LookupSwitchInsnNode lookupSwitch -> {
                    link(block, byLeader.get(lookupSwitch.dflt));
                    lookupSwitch.labels.forEach(label -> link(block, byLeader.get(label)));
                }
                default -> link(block, fallThrough(blocks, i)); //just fall into the next block
            }
        }
    }

    private BasicBlock fallThrough(List<BasicBlock> blocks, int i) {
        return i + 1 < blocks.size() ? blocks.get(i + 1) : null;
    }

    private void link(BasicBlock from, BasicBlock to) {
        if (to == null) {
            return;
        }
        from.getSuccessors().add(to);
        to.getPredecessors().add(from);
    }

    public List<BasicBlock> buildBlocks(MethodNode method) {
        List<BasicBlock> blocks = new ArrayList<>();

        Set<AbstractInsnNode> leaders = findLeaders(method);
        AbstractInsnNode[] insns = method.instructions.toArray();

        BasicBlock current = null;
        for (AbstractInsnNode insn : insns) {
            if (leaders.contains(insn)) {
                current = new BasicBlock(blocks.size());
                blocks.add(current);
            }

            current.getInstructions().add(insn); // current is never null
        }

        return blocks;
    }

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
