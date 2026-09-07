package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.config.SeedCatalogsCommand;

/**
 * La carga inicial de catalogos deja el sistema listo sin dejar de ser administrable.
 *
 * <p>Lo importante es que sea idempotente y que <b>no deshaga un renombrado</b>: si
 * el area cambio un valor, volver a ejecutar el comando no debe devolverlo al
 * original.
 */
class SeedCatalogsIT extends PostgresIntegrationTest {

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private SeedCatalogsCommand carga;

    @BeforeEach
    void datos() {
        SesionDePrueba.limpiar(jdbc);
    }

    private List<String> valoresDe(String tabla) {
        return jdbc.sql("SELECT name FROM " + tabla + " ORDER BY lower(btrim(name))")
                .query(String.class).list();
    }

    @Test
    @DisplayName("sin cuenta de jefatura no carga nada y lo explica")
    void sinJefaturaNoCarga() {
        String informe = carga.ejecutar();

        assertThat(informe).contains("bootstrap");
        assertThat(valoresDe("procedural_status")).isEmpty();
    }

    @Test
    @DisplayName("carga los cinco estados judiciales que enumera el insumo")
    void cargaEstadosJudiciales() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar();

        assertThat(valoresDe("procedural_status")).containsExactlyInAnyOrder(
                "Pendiente de actuación", "En trámite", "Concluido", "Archivado", "Ejecución");
    }

    @Test
    @DisplayName("carga los cinco catalogos del sistema")
    void cargaLosCinco() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar();

        assertThat(valoresDe("administrative_status")).hasSize(5);
        assertThat(valoresDe("pending_task_type")).hasSize(13);
        assertThat(valoresDe("priority")).hasSize(3);
        assertThat(valoresDe("pending_task_status")).hasSize(6);
    }

    @Test
    @DisplayName("ejecutarlo dos veces no duplica nada")
    void idempotente() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar();
        carga.ejecutar();

        assertThat(valoresDe("procedural_status")).hasSize(5);
        assertThat(valoresDe("pending_task_type")).hasSize(13);
    }

    @Test
    @DisplayName("no repone un valor que el area renombro")
    void noReponeLosRenombrados() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        carga.ejecutar();

        jdbc.sql("""
                UPDATE procedural_status SET name = 'En trámite judicial'
                WHERE lower(btrim(name)) = 'en trámite'
                """).update();

        carga.ejecutar();

        // Un comando de carga no puede deshacer una decision del area. Por eso solo
        // carga catalogos vacios: comprobar valor por valor repondria el original y
        // dejaria dos donde el area queria uno.
        assertThat(valoresDe("procedural_status"))
                .contains("En trámite judicial").doesNotContain("En trámite")
                .hasSize(5);
    }

    @Test
    @DisplayName("no carga en un catalogo que ya tiene valores")
    void noTocaCatalogosConValores() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");
        jdbc.sql("""
                INSERT INTO procedural_status (id, name, enabled, created_by,
                                               created_at, updated_at, version)
                SELECT gen_random_uuid(), 'Unico estado del area', true, id, now(), now(), 1
                FROM app_user LIMIT 1
                """).update();

        String informe = carga.ejecutar();

        assertThat(informe).contains("ya tenia valores");
        assertThat(valoresDe("procedural_status")).containsExactly("Unico estado del area");
    }

    @Test
    @DisplayName("cada valor cargado queda auditado")
    void quedaAuditado() {
        SesionDePrueba.crearCuenta(jdbc, encoder, "jefa@ejemplo.test", "HEAD");

        carga.ejecutar();

        Integer eventos = jdbc.sql("""
                SELECT count(*) FROM audit_event
                WHERE reason = 'Carga inicial desde el insumo del cliente'
                """).query(Integer.class).single();

        assertThat(eventos).as("quien pregunte de donde salio un valor debe poder averiguarlo")
                .isEqualTo(5 + 5 + 13 + 3 + 6);
    }

    @Test
    @DisplayName("los catalogos siguen arrancando vacios: la carga es un acto aparte")
    void catalogosVaciosPorDefecto() {
        // Sin llamar al comando, nada se carga solo.
        assertThat(valoresDe("procedural_status")).isEmpty();
        assertThat(valoresDe("pending_task_status")).isEmpty();
    }
}
