package pe.Planilla.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Sirve la pantalla de Angular desde el mismo servidor que la API (carpeta src/main/resources/static).
 * Cualquier ruta que no sea un archivo (por ejemplo /empleados al refrescar la página) devuelve index.html para que
 * el router de Angular la resuelva. Si no hay pantalla incluida (desarrollo con ng serve), no hace nada.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // La API y el WebSocket nunca devuelven la pantalla
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws")) {
                            return null;
                        }
                        Resource pedido = location.createRelative(resourcePath);
                        if (pedido.exists() && pedido.isReadable()) {
                            return pedido;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
