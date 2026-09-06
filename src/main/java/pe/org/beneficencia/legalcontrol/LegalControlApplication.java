package pe.org.beneficencia.legalcontrol;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class LegalControlApplication {

    public static void main(String[] args) {
        // Los modos CLI (--app.command=...) no levantan servidor web: hacen su
        // trabajo y terminan. Evita exponer un puerto durante el arranque inicial.
        boolean modoComando = Arrays.stream(args).anyMatch(a -> a.startsWith("--app.command="));

        new SpringApplicationBuilder(LegalControlApplication.class)
                .web(modoComando ? WebApplicationType.NONE : WebApplicationType.SERVLET)
                .run(args);
    }
}
