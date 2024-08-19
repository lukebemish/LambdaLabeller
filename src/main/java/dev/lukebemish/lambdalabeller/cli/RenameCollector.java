package dev.lukebemish.lambdalabeller.cli;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class RenameCollector extends ClassVisitor {
    final Set<String> unknownNames = new HashSet<>();
    final Map<String, String> renames = new HashMap<>();

    protected RenameCollector() {
        super(Opcodes.ASM9);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if ((access & Opcodes.ACC_SYNTHETIC) != 0 && (access & Opcodes.ACC_PRIVATE) != 0 && name.startsWith("lambda$")) {
            boolean[] found = {false};
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName, String descriptor, boolean isInterface) {
                    if (owner.equals(Shared.LABEL_CLASS)) {
                        renames.put(name, methodName);
                        found[0] = true;
                    }
                }

                @Override
                public void visitEnd() {
                    if (!found[0]) {
                        unknownNames.add(name);
                    }
                }
            };
        } else {
            return null;
        }
    }
}
