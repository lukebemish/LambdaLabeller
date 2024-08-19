package dev.lukebemish.lambdalabeller.jst;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import net.neoforged.jst.api.Replacement;
import net.neoforged.jst.api.Replacements;
import net.neoforged.jst.api.SourceTransformer;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

@CommandLine.Command(name = "lambda-label-prepare", description = "Prepares labelled lambdas in decompiled source for label removal")
public class LambdaLabelPrepareTransformer implements SourceTransformer {
    @Override
    public void visitFile(PsiFile psiFile, Replacements replacements) {
        new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                switch (element) {
                    case PsiMethodCallExpression methodCallExpression -> {
                        var qualifier = methodCallExpression.getMethodExpression().getQualifierExpression();
                        if (qualifier != null && !qualifier.getText().equals(Shared.LABEL_CLASS_NAME) && !qualifier.getText().equals(Shared.LABEL_CLASS.replace('/', '.'))) {
                            super.visitElement(element);
                            return;
                        }
                        var psiElement = methodCallExpression.getMethodExpression().resolve();
                        if (psiElement instanceof PsiMethod psiMethod && psiMethod.getContainingClass() != null) {
                            if (Shared.binaryName(psiMethod.getContainingClass()).equals(Shared.LABEL_CLASS)) {
                                PsiElement toClear = methodCallExpression;
                                if (toClear.getParent() instanceof PsiExpressionStatement statement) {
                                    toClear = statement;
                                } else if (toClear.getParent() instanceof PsiLambdaExpression lambdaExpression) {
                                    replacements.add(new Replacement(
                                            lambdaExpression.getBody().getTextRange(),
                                            "{" + Shared.LABEL_CLASS.replace('/', '.') + "." + psiMethod.getName() + "();}"
                                    ));
                                    return;
                                }
                                var previous = toClear.getPrevSibling();
                                while (previous instanceof PsiWhiteSpace) {
                                    previous = previous.getPrevSibling();
                                }
                                var start = previous == null ? methodCallExpression.getTextRange().getStartOffset() : previous.getTextRange().getEndOffset();
                                replacements.add(new Replacement(
                                        new TextRange(start, methodCallExpression.getTextRange().getEndOffset()),
                                        Shared.LABEL_CLASS.replace('/', '.') + "." + psiMethod.getName() + "()"
                                ));
                                return;
                            }
                        }
                    }
                    case PsiMethodReferenceExpression methodReferenceExpression -> {
                        var qualifier = methodReferenceExpression.getQualifierExpression();
                        if (qualifier != null && !qualifier.getText().equals(Shared.LABEL_CLASS_NAME) && !qualifier.getText().equals(Shared.LABEL_CLASS.replace('/', '.'))) {
                            super.visitElement(element);
                            return;
                        }
                        var reference = methodReferenceExpression.resolve();
                        if (reference instanceof PsiMethod psiMethod) {
                            if (psiMethod.getContainingClass() != null) {
                                if (Shared.binaryName(psiMethod.getContainingClass()).equals(Shared.LABEL_CLASS)) {
                                    var previous = methodReferenceExpression.getPrevSibling();
                                    while (previous != null && !(previous instanceof PsiWhiteSpace)) {
                                        previous = previous.getPrevSibling();
                                    }
                                    var start = previous == null ? methodReferenceExpression.getTextRange().getStartOffset() : previous.getTextRange().getEndOffset();
                                    replacements.add(new Replacement(
                                            new TextRange(start, methodReferenceExpression.getTextRange().getEndOffset()),
                                            "() -> {"+Shared.LABEL_CLASS.replace('/', '.')+"."+psiMethod.getName()+"();}"
                                    ));
                                    return;
                                }
                            }
                        }
                    }
                    case PsiImportStatementBase importStatement -> {
                        if (importStatement.resolve() instanceof PsiClass psiClass) {
                            if (Shared.binaryName(psiClass).equals(Shared.LABEL_CLASS)) {
                                var previous = importStatement.getPrevSibling();
                                while (previous != null && !(previous instanceof PsiWhiteSpace)) {
                                    previous = previous.getPrevSibling();
                                }
                                var start = previous == null ? importStatement.getTextRange().getStartOffset() : previous.getTextRange().getEndOffset();
                                replacements.add(new Replacement(new TextRange(start, importStatement.getTextRange().getEndOffset()), ""));
                                return;
                            }
                        }
                    }
                    default -> {}
                }
                super.visitElement(element);
            }
        }.visitElement(psiFile);
    }
}
