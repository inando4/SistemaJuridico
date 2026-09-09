package pe.org.beneficencia.legalcontrol.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureForm;
import pe.org.beneficencia.legalcontrol.administrativeprocedure.AdministrativeProcedureService;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseForm;
import pe.org.beneficencia.legalcontrol.judicialcase.JudicialCaseService;

/**
 * Las series de numeracion judicial y administrativa son independientes.
 *
 * <p>Confirmado por el cliente: identifican expedientes de naturaleza distinta y
 * cada registro lleva la suya. Si en el futuro resultara que el area usa una serie
 * unica, esta prueba seria la primera en cambiar.
 */
class IndependentNumberingIT extends PostgresIntegrationTest {

    private static final String MISMO_NUMERO = "00123-2026";

    @Autowired private JdbcClient jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JudicialCaseService judiciales;
    @Autowired private AdministrativeProcedureService administrativos;

    private UUID abogado;

    @BeforeEach
    void cuenta() {
        SesionDePrueba.limpiar(jdbc);
        abogado = SesionDePrueba.crearCuenta(jdbc, encoder, "abogado@ejemplo.test", "LAWYER");
    }

    @Test
    @DisplayName("un mismo numero puede existir como judicial y como administrativo")
    void seriesIndependientes() {
        var judicial = judiciales.crear(new JudicialCaseForm(null, MISMO_NUMERO, null, null, null,
                null, null, null, null, null, null, null, null, null, null), abogado);
        assertThat(judicial.correcto()).isTrue();

        var administrativo = administrativos.crear(new AdministrativeProcedureForm(
                null, MISMO_NUMERO, null, null, null, null, null, null, null), abogado);

        assertThat(administrativo.correcto())
                .as("las series no se comparten: el numero no colisiona").isTrue();

        Integer judicialesConEseNumero = jdbc.sql(
                        "SELECT count(*) FROM judicial_case WHERE case_number = :n")
                .param("n", MISMO_NUMERO).query(Integer.class).single();
        Integer administrativosConEseNumero = jdbc.sql(
                        "SELECT count(*) FROM administrative_procedure WHERE file_number = :n")
                .param("n", MISMO_NUMERO).query(Integer.class).single();

        assertThat(judicialesConEseNumero).isEqualTo(1);
        assertThat(administrativosConEseNumero).isEqualTo(1);
    }

    @Test
    @DisplayName("dentro de cada registro el numero sigue siendo unico")
    void unicidadDentroDeCadaRegistro() {
        administrativos.crear(new AdministrativeProcedureForm(
                null, MISMO_NUMERO, null, null, null, null, null, null, null), abogado);

        var repetido = administrativos.crear(new AdministrativeProcedureForm(
                null, MISMO_NUMERO, null, null, null, null, null, null, null), abogado);

        assertThat(repetido.correcto()).isFalse();
    }
}
