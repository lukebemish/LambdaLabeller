package dev.lukebemish.lambdalabeller.jst;

import com.google.auto.service.AutoService;
import net.neoforged.jst.api.SourceTransformer;
import net.neoforged.jst.api.SourceTransformerPlugin;

@AutoService(SourceTransformerPlugin.class)
public class LambdaLabelPreparePlugin implements SourceTransformerPlugin {
    @Override
    public String getName() {
        return "lambda-label-prepare";
    }

    @Override
    public SourceTransformer createTransformer() {
        return new LambdaLabelPrepareTransformer();
    }
}
