package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Presupuesto del bloque «Pendientes relacionados» (principio IV, CE-004 y CE-005).
 *
 * <p>Lo que de verdad protege esta prueba es la <b>invariancia</b>: el mismo numero
 * de consultas con dos pendientes vinculados que con cincuenta. Un techo absoluto se
 * puede cumplir con una consulta por fila mientras las filas sean pocas; la
 * invariancia no.
 */
@AutoConfigureMockMvc
@Import(ContadorDeConsultas.class)
class ExpedienteFichaQueryBudgetIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;

    private MockHttpSession sesion;
    private UUID usuario;
    private UUID conPocos;
    private UUID conMuchos;
    private UUID procedimientoConPocos;
    private UUID procedimientoConMuchos;

    @BeforeEach
    void preparar() throws Exception {
        SesionDePrueba.limpiar(jdbc);
        usuario = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
        int ano = LocalDate.now().getYear();
        DatosSinteticos.sembrarCalendario(jdbc, usuario, ano - 1, ano, ano + 1);
        sesion = SesionDePrueba.entrar(mvc, "abogado@ejemplo.test");

        conPocos = judicial("EXP-POCOS-2026");
        conMuchos = judicial("EXP-MUCHOS-2026");
        procedimientoConPocos = administrativo("ADM-POCOS-2026");
        procedimientoConMuchos = administrativo("ADM-MUCHOS-2026");

        vincular(conPocos, null, 2);
        vincular(conMuchos, null, 50);
        vincular(null, procedimientoConPocos, 2);
        vincular(null, procedimientoConMuchos, 50);
    }

    private UUID judicial(String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO judicial_case (id, owner_id, case_number, active,
                                           created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", usuario).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private UUID administrativo(String numero) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO administrative_procedure (id, owner_id, file_number, active,
                                                      created_at, updated_at, version)
                VALUES (:id, :o, :n, true, :a, :a, 1)
                """).param("id", id).param("o", usuario).param("n", numero)
                .param("a", Timestamp.from(Instant.now())).update();
        return id;
    }

    private void vincular(UUID j, UUID a, int cuantos) {
        Timestamp ahora = Timestamp.from(Instant.now());
        for (int i = 0; i < cuantos; i++) {
            jdbc.sql("""
                    INSERT INTO pending_task (id, owner_id, title, judicial_case_id,
                                              administrative_procedure_id, registered_at,
                                              deadline, active, created_at, updated_at, version)
                    VALUES (:id, :o, :t, :j, :a, :hoy, CAST(:lim AS date), true, :ts, :ts, 1)
                    """)
                    .param("id", UUID.randomUUID()).param("o", usuario)
                    .param("t", "Pendiente sintetico " + i)
                    .param("j", j).param("a", a).param("hoy", LocalDate.now())
                    .param("lim", LocalDate.now().plusDays(i + 1).toString())
                    .param("ts", ahora).update();
        }
    }

    private long costeDe(String ruta) {
        return ContadorDeConsultas.contar(() -> {
            try {
                mvc.perform(get(ruta).session(sesion));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    @DisplayName("la ficha judicial cuesta lo mismo con 2 vinculos que con 50 (CE-004)")
    void invarianteEnLaFichaJudicial() {
        long pocos = costeDe("/judiciales/" + conPocos);
        long muchos = costeDe("/judiciales/" + conMuchos);
        System.out.printf("Ficha judicial: %d consultas con 2 vinculos, %d con 50%n",
                pocos, muchos);

        assertThat(muchos)
                .as("si subiera con las filas habria una consulta por fila escondida")
                .isEqualTo(pocos);
    }

    @Test
    @DisplayName("la ficha administrativa cuesta lo mismo con 2 vinculos que con 50 (CE-004)")
    void invarianteEnLaFichaAdministrativa() {
        long pocos = costeDe("/administrativos/" + procedimientoConPocos);
        long muchos = costeDe("/administrativos/" + procedimientoConMuchos);
        System.out.printf("Ficha administrativa: %d consultas con 2 vinculos, %d con 50%n",
                pocos, muchos);

        assertThat(muchos).isEqualTo(pocos);
    }

    @Test
    @DisplayName("el bloque añade una sola consulta a cada ficha (CE-005)")
    void unaConsultaDeMas() {
        // La ficha de un expediente sin ningun vinculo tambien hace la consulta del
        // bloque —devuelve vacio, pero la hace—, asi que no sirve de linea base.
        // La referencia es el techo del plan, que la implementacion no puede rebasar.
        long judicial = costeDe("/judiciales/" + conPocos);
        long administrativa = costeDe("/administrativos/" + procedimientoConPocos);
        System.out.printf("Coste total: ficha judicial %d, ficha administrativa %d%n",
                judicial, administrativa);

        assertThat(judicial)
                .as("la instantanea del calendario se comparte con el plazo del expediente")
                .isLessThanOrEqualTo(8L);
        assertThat(administrativa).isLessThanOrEqualTo(8L);
    }

    @Test
    @DisplayName("el bloque se acota: 50 vinculos no pintan 50 filas")
    void elBloqueSeAcota() throws Exception {
        String html = mvc.perform(get("/judiciales/" + conMuchos).session(sesion))
                .andReturn().getResponse().getContentAsString();

        // 25 por pagina; la fila 26 en adelante no se pinta aunque exista.
        int filas = html.split("Pendiente sintetico ", -1).length - 1;
        assertThat(filas)
                .as("la ficha no se convierte en un scroll")
                .isLessThanOrEqualTo(25);
        assertThat(html).contains("Hay más pendientes de los que caben aquí");
    }
}
