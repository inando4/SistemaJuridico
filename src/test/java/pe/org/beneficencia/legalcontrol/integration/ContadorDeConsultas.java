package pe.org.beneficencia.legalcontrol.integration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Cuenta las sentencias SQL que la aplicacion prepara de verdad.
 *
 * <p>Sustituye a la lectura de {@code pg_stat_database}, que no sirve para esto:
 * el recolector de estadisticas de PostgreSQL se actualiza con retraso, asi que
 * la medicion base puede devolver un valor anterior a lo que se acaba de hacer y
 * la diferencia sale inflada. Aqui se cuenta en el momento y sin margen de error.
 *
 * <p>Se envuelve la conexion con un proxy que incrementa el contador en cada
 * {@code prepareStatement}, que es exactamente una consulta enviada al servidor.
 */
@TestConfiguration
public class ContadorDeConsultas {

    private static final AtomicLong CONSULTAS = new AtomicLong();

    public static void reiniciar() {
        CONSULTAS.set(0);
    }

    public static long total() {
        return CONSULTAS.get();
    }

    /** Ejecuta algo y devuelve cuantas consultas costo. */
    public static long contar(Runnable trabajo) {
        long antes = total();
        trabajo.run();
        return total() - antes;
    }

    /**
     * Envuelve el DataSource despues de crearlo.
     *
     * <p>Un {@code @Bean} que recibiera el DataSource para sustituirlo se pediria a
     * si mismo y Spring lo rechazaria como referencia circular. Un post-procesador
     * actua sobre el bean ya construido y no tiene ese problema.
     */
    @org.springframework.context.annotation.Bean
    public static BeanPostProcessor envoltorioContador() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (!(bean instanceof DataSource original) || bean instanceof DelegatingDataSource) {
                    return bean;
                }
                return new DelegatingDataSource(original) {
                    @Override
                    public Connection getConnection() throws java.sql.SQLException {
                        return envolverConexion(super.getConnection());
                    }

                    @Override
                    public Connection getConnection(String usuario, String clave)
                            throws java.sql.SQLException {
                        return envolverConexion(super.getConnection(usuario, clave));
                    }
                };
            }
        };
    }

    static Connection envolverConexion(Connection real) {
        InvocationHandler manejador = (proxy, metodo, args) -> {
            if (metodo.getName().startsWith("prepare") || "createStatement".equals(metodo.getName())) {
                CONSULTAS.incrementAndGet();
            }
            try {
                return metodo.invoke(real, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, manejador);
    }
}
