package dev.lukebemish.lambdalabeller.cli;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

final class MethodLabeller extends ClassVisitor {
    private final @Nullable Set<String> names;

    MethodLabeller(@Nullable Set<String> names, ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
        this.names = names;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
        if ((access & Opcodes.ACC_SYNTHETIC) != 0 && (access & Opcodes.ACC_PRIVATE) != 0 && name.startsWith("lambda$")) {
            if (names != null) {
                names.add(name);
            }
            return new MethodVisitor(Opcodes.ASM9, delegate) {
                @Override
                public void visitCode() {
                    super.visitCode();
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            Shared.LABEL_CLASS,
                            name,
                            "()V",
                            true
                    );
                }
            };
        }
        return delegate;
    }
}
