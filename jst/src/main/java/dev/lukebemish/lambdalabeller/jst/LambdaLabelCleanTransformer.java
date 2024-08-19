package dev.lukebemish.lambdalabeller.jst;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiRecursiveElementVisitor;
import net.neoforged.jst.api.Replacement;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

@CommandLine.Command(name = "lambda-label-clean", description = "Removes lambda labels")
public class LambdaLabelCleanTransformer implements SourceTransformer {
    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiMethodCallExpression methodCallExpression) {
                    var qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
                    if (qualifier != null && !qualifier.getText().equals(Shared.LABEL_CLASS_NAME) && !qualifier.getText().equals(Shared.LABEL_CLASS.replace('/', '.'))) {
                        super.visitElement(element);
                        return;
                    }
                    var psiMethod = methodCallExpression.resolveMethod();
                    PsiElement toRemove = methodCallExpression;
                    if (toRemove.getParent() instanceof PsiExpressionStatement statement) {
                        toRemove = statement;
                    }
                    if (psiMethod != null && psiMethod.getContainingClass() != null) {
                        if (Shared.binaryName(psiMethod.getContainingClass()).equals(Shared.LABEL_CLASS)) {
                            replacements.add(new Replacement(
                                    toRemove.getTextRange(),
                                    "\n".repeat((int) toRemove.getText().lines().count() - 1)
                            ));
                            return;
                        }
                    }
                }
                super.visitElement(element);
            }
        }.visitElement(psiFile);
    }
}
