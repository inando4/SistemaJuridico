package pe.org.beneficencia.legalcontrol.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.CssLinkResourceTransformer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

/** Versiona archivos publicos sin buscar las rutas de negocio en el disco. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class StaticResourcesConfig implements WebMvcConfigurer {

    private final WebProperties.Resources recursos;

    public StaticResourcesConfig(WebProperties propiedades) {
        this.recursos = propiedades.getResources();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        for (String carpeta : List.of("css", "js", "vendor")) {
            registro.addResourceHandler("/" + carpeta + "/**")
                    .addResourceLocations("classpath:/static/" + carpeta + "/")
                    .setCacheControl(recursos.getCache().getCachecontrol().toHttpCacheControl())
                    .setUseLastModified(recursos.getCache().isUseLastModified())
                    .resourceChain(recursos.getChain().isCache())
                    .addResolver(new VersionResourceResolver().addContentVersionStrategy(
                            recursos.getChain().getStrategy().getContent().getPaths()))
                    .addTransformer(new CssLinkResourceTransformer());
        }
    }
}
