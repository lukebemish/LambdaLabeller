package dev.lukebemish.lambdalabeller.jst;

import com.intellij.psi.PsiClass;
import net.neoforged.jst.api.PsiHelper;

final class Shared {
    private Shared() {}

    static final String LABEL_CLASS = "dev/lukebemish/lambdalabeller/LabelledLambda";
    static final String LABEL_CLASS_NAME = "LabelledLambda";

    static String binaryName(PsiClass psiClass) {
        StringBuilder builder = new StringBuilder();
        PsiHelper.getBinaryClassName(psiClass, builder);
        return builder.toString();
    }
}
