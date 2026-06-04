package me.xiongfan.photostorylinker;

import org.pf4j.Extension;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import reactor.core.publisher.Mono;
import run.halo.app.theme.dialect.TemplateFooterProcessor;

@Extension
@Component
public class PhotoStoryTemplateFooterProcessor implements TemplateFooterProcessor {

    @Override
    public Mono<Void> process(ITemplateContext context, IProcessableElementTag tag,
        IElementTagStructureHandler structureHandler, IModel model) {
        var factory = context.getModelFactory();
        model.add(factory.createText("\n"));
        model.add(factory.createStandaloneElementTag("link",
            "rel", "stylesheet", false, false));
        var link = model.get(model.size() - 1);
        var stylesheet = factory.setAttribute((IProcessableElementTag) link,
            "href", "/apis/api.photo-story-linker.xiongfan.me/v1alpha1/assets/story.css");
        model.replace(model.size() - 1, stylesheet);
        model.add(factory.createText("\n"));
        model.add(factory.createOpenElementTag("script",
            "src", "/apis/api.photo-story-linker.xiongfan.me/v1alpha1/assets/story.js", false));
        var script = model.get(model.size() - 1);
        script = factory.setAttribute((IProcessableElementTag) script, "defer", "defer");
        model.replace(model.size() - 1, script);
        model.add(factory.createCloseElementTag("script"));
        model.add(factory.createText("\n"));
        return Mono.empty();
    }
}
